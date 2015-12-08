package org.dogecoin.sporkwallet;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.core.listeners.PeerConnectionEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.WalletCoinEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.MonetaryFormat;
import org.libdohj.params.DogecoinMainNetParams;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

public class Controller implements Initializable {

    @FXML private Label myAddress;
    @FXML private Label status;
    @FXML private Label myBalance;
    @FXML private ListView<String> txList;
    @FXML private TextField sendAddress;
    @FXML private TextField sendAmount;
    @FXML private Button sendButton;

    private NetworkParameters params = DogecoinMainNetParams.get();
    private MonetaryFormat format = new MonetaryFormat().shift(0).minDecimals(4).repeatOptionalDecimals(2, 2).code(0, "DOGE").code(3, "mDOGE").code(6, "µDOGE");
    private WalletAppKit kit;

    private ObservableList<String> txListValues = FXCollections.observableArrayList();
    private int globalBlocksLeft;
    private int globalPeersConnected;

    public void initialize(URL location, ResourceBundle resources) {
        status.setText("Connecting...");
        txList.setItems(txListValues);

        kit = new WalletAppKit(params, new File("."), "sporkwallet") {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setConnectTimeoutMillis(1000);
                System.out.println(kit.wallet());

                peerGroup().addDataEventListener(new PeerDataEventListener() {
                    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, final int blocksLeft) {
                        globalBlocksLeft = blocksLeft;
                        updateStatus();
                    }

                    public void onChainDownloadStarted(Peer peer, int blocksLeft) {

                    }

                    public Message onPreMessageReceived(Peer peer, Message m) {
                        return null;
                    }

                    @Nullable
                    public List<Message> getData(Peer peer, GetDataMessage m) {
                        return null;
                    }
                });

                peerGroup().addConnectionEventListener(new PeerConnectionEventListener() {
                    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {

                    }

                    public void onPeerConnected(Peer peer, int peerCount) {
                        globalPeersConnected = peerCount;
                        updateStatus();
                    }

                    public void onPeerDisconnected(Peer peer, int peerCount) {
                        globalPeersConnected = peerCount;
                        updateStatus();
                    }
                });

                wallet().addCoinEventListener(new WalletCoinEventListener() {
                    public void onCoinsReceived(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
                        if (newBalance.minus(prevBalance).isPositive()) {
                            Platform.runLater(new Runnable() {
                                public void run() {
                                    txListValues.add(String.format("%1$s: Received %2$s", new SimpleDateFormat("dd.MM.yy hh:mm").format(tx.getUpdateTime()), format.format(newBalance.minus(prevBalance))));
                                }
                            });
                        }
                    }

                    public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
                        Platform.runLater(new Runnable() {
                            public void run() {
                                txListValues.add(String.format("%1$s: Sent %2$s", new SimpleDateFormat("dd.MM.yy hh:mm").format(tx.getUpdateTime()), format.format(prevBalance.minus(newBalance))));
                            }
                        });
                    }
                });

                Platform.runLater(new Runnable() {
                    public void run() {
                        myAddress.setText(wallet().currentReceiveAddress().toString());
                        myBalance.setText(format.format(wallet().getBalance()).toString());

                        for (Transaction tx : wallet().getTransactions(false)) {
                            if (tx.getValue(wallet()).isNegative()) {
                                txListValues.add(String.format("%1$s: Sent %2$s", new SimpleDateFormat("dd.MM.yy hh:mm").format(tx.getUpdateTime()), format.format(tx.getValue(wallet()).negate())));
                            } else {
                                txListValues.add(String.format("%1$s: Received %2$s", new SimpleDateFormat("dd.MM.yy hh:mm").format(tx.getUpdateTime()), format.format(tx.getValue(wallet()))));
                            }
                        }

                        sendButton.setOnAction(new EventHandler<ActionEvent>() {
                            public void handle(ActionEvent event) {
                                // TODO validate!
                                SendRequest req = SendRequest.to(Address.fromBase58(params, sendAddress.getText()), Coin.parseCoin(sendAmount.getText()));
                                try {
                                    wallet().sendCoins(req);
                                } catch (InsufficientMoneyException e) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Insufficient Money");
                                    alert.setHeaderText("You don't have enough money!");
                                    alert.setContentText("You need " + (e.missing == null ? "an unknown amount" : format.format(e.missing)) + " more");

                                    alert.showAndWait();
                                }
                            }
                        });
                    }
                });
            }
        };

        kit.startAsync();
    }

    private void updateStatus() {
        Platform.runLater(new Runnable() {
            public void run() {
                if (globalBlocksLeft <= 0) {
                    status.setText(String.format("In snyc. | Peers: %1$d", globalPeersConnected));
                } else {
                    status.setText(String.format("Syncing... %1$d blocks downloaded | %2$d blocks to go | Peers: %3$d", kit.chain().getBestChainHeight(), globalBlocksLeft, globalPeersConnected));
                }

                myAddress.setText(kit.wallet().currentReceiveAddress().toString());
                myBalance.setText(format.format(kit.wallet().getBalance()).toString());
            }
        });
    }
}
