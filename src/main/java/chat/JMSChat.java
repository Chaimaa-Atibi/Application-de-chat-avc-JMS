package chat;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;

public class JMSChat extends Application {
    private MessageProducer messageProducer;
    private Session session;
    private String codeUser;
    public static void main(String[] args) {
        Application.launch(JMSChat.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("JMS Chat");
        BorderPane root=new BorderPane();
        HBox hBox=new HBox();
        hBox.setPadding(new Insets(10));
        hBox.setSpacing(10);
        hBox.setBackground(new Background(
                new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY)
        ));

        Label labelCode=new Label("Code: ");
        TextField textFieldCode=new TextField("C1");
        textFieldCode.setPromptText("Code");

        Label labelHost=new Label("Host: ");
        TextField textFieldHost=new TextField("localhost");
        textFieldHost.setPromptText("Host");

        Label labelPort=new Label("Port: ");
        TextField textFieldPort=new TextField("61616");
        textFieldPort.setPromptText("Port");

        Button buttonConnect=new Button("Connecter");

        hBox.getChildren().addAll(
                labelCode, textFieldCode,
                labelHost, textFieldHost,
                labelPort, textFieldPort,
                buttonConnect);

        root.setTop(hBox);

        VBox vBox=new VBox();
        GridPane gridPane=new GridPane();
        HBox hBox1=new HBox();
        vBox.getChildren().addAll(gridPane, hBox1);
        root.setCenter(vBox);

        Label labelTo=new Label("To:");
        TextField textFieldTo=new TextField("C1");
        textFieldTo.setPrefWidth(250);
        Label labelMessage=new Label("Message:");
        TextArea textAreaMessage=new TextArea();
        textAreaMessage.setPrefWidth(250);
        Button buttonEnvoyer=new Button("Envoyer");
        Label labelImage=new Label("Image:");
        File file=new File("images");
        ObservableList<String> observableListImages=
                FXCollections.observableArrayList(file.list());
        ComboBox<String> comboBoxImages=new ComboBox<>(observableListImages);
        comboBoxImages.getSelectionModel().select(0);
        Button buttonEnvoyerImage=new Button("Envoyer Image");

        gridPane.setPadding(new Insets(10));
        gridPane.setVgap(10);
        gridPane.setHgap(10);
        textAreaMessage.setPrefRowCount(2);

        gridPane.add(labelTo, 0, 0);
        gridPane.add(textFieldTo, 1, 0);
        gridPane.add(labelMessage, 0, 1);
        gridPane.add(textAreaMessage, 1, 1);
        gridPane.add(buttonEnvoyer, 2, 1);
        gridPane.add(labelImage, 0, 2);
        gridPane.add(comboBoxImages, 1, 2);
        gridPane.add(buttonEnvoyerImage, 2, 2);

        ObservableList<String> observableListMessages=
                FXCollections.observableArrayList();
        ListView<String> listViewMessages=new ListView<>(observableListMessages);

        File file1=new File("images/"+comboBoxImages.getSelectionModel().getSelectedItem());
        Image image=new Image(file1.toURI().toString());
        ImageView imageView=new ImageView(image);
        imageView.setFitHeight(240);
        imageView.setFitWidth(320);
        hBox1.getChildren().addAll(listViewMessages, imageView);
        hBox1.setPadding(new Insets(10));
        hBox1.setSpacing(10);

        Scene scene=new Scene(root, 800, 500);
        primaryStage.setScene(scene);
        primaryStage.show();

        comboBoxImages.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                        File file2=new File("images/"+newValue);
                        Image image1=new Image(file2.toURI().toString());
                        imageView.setImage(image1);
                    }
                });

        buttonEnvoyer.setOnAction(event -> {
            TextMessage textMessage= null;
            try {
                textMessage = session.createTextMessage();
                textMessage.setText(textAreaMessage.getText());
                textMessage.setStringProperty("code", textFieldTo.getText());
                messageProducer.send(textMessage);
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });

        buttonEnvoyerImage.setOnAction(event -> {
            try {
                StreamMessage streamMessage=session.createStreamMessage();
                streamMessage.setStringProperty("code", textFieldTo.getText());
                File f=new File("images/"+comboBoxImages.getSelectionModel().getSelectedItem());
                FileInputStream fileInputStream=new FileInputStream(f);
                byte[] data=new byte[(int) f.length()];
                fileInputStream.read(data);
                streamMessage.writeString(comboBoxImages.getSelectionModel().getSelectedItem());
                streamMessage.writeInt(data.length);
                streamMessage.writeBytes(data);
                messageProducer.send(streamMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        buttonConnect.setOnAction(event -> {
            try {
                codeUser=textFieldCode.getText();
                String host=textFieldHost.getText();
                int port=Integer.parseInt(textFieldPort.getText());
                ConnectionFactory connectionFactory=new ActiveMQConnectionFactory(
                        "tcp://"+host+":"+port
                );
                Connection connection=connectionFactory.createConnection();
                connection.start();
                session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination=session.createTopic("enset.chat");
                MessageConsumer messageConsumer=session.createConsumer(destination,"code='"+codeUser+"'");
                messageProducer=session.createProducer(destination);
                messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                messageConsumer.setMessageListener(message -> {
                    try {
                        if(message instanceof TextMessage){
                            TextMessage textMessage=(TextMessage) message;
                            observableListMessages.add(textMessage.getText());
                        }
                        else if(message instanceof StreamMessage){
                            StreamMessage streamMessage=(StreamMessage) message;
                            String nomPhoto=streamMessage.readString();
                            observableListImages.add("Réception d'une photo:"+nomPhoto);
                            int size=streamMessage.readInt();
                            byte[] data=new byte[size];
                            streamMessage.readBytes(data);
                            ByteArrayInputStream byteArrayInputStream=new ByteArrayInputStream(data);
                            Image image1=new Image(byteArrayInputStream);
                            imageView.setImage(image1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                hBox.setDisable(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
