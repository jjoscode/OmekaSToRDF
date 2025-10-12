package jdv.omekastordf;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;


import javafx.event.ActionEvent;

import java.util.ArrayList;

public class ViewController {
    @FXML
    private TextField txtfldURLDB;
    @FXML
    private TextField txtfldUsername;
    @FXML
    private TextField txtfldPassword;
    @FXML
    private TextField txtfldBaseIRI;

    @FXML
    private Label lblFormato;
    @FXML
    private Label lblErrore;

    @FXML ListView<String> txtOutput;

    @FXML
    private TextArea txtType;

    @FXML
    private TextArea txtPIRI;


    @FXML
    protected void showFormat(ActionEvent event) {
        MenuItem item = (MenuItem) event.getSource();
        String formato = item.getText();
        lblFormato.setText(formato);
        System.out.println("Formato selezionato: " + formato);
    }

    @FXML
    protected void generateGraph(ActionEvent event) {
        lblErrore.setText("");
        String[] args = new String[7];
        args[0] = lblFormato.getText();
        args[1] = txtfldURLDB.getCharacters().toString();
        args[2] = txtfldUsername.getCharacters().toString();
        args[3] = txtfldPassword.getCharacters().toString();
        args[4] = txtfldBaseIRI.getCharacters().toString();
        args[5] = txtType.getText();
        args[6] = txtPIRI.getText();
        ArrayList<String> toDisplay = new ArrayList<String>(OmekaSToRDF.generateGraph(args));
        if(OmekaSToRDF.isValidBaseIRI(args[4]) && OmekaSToRDF.isValidFormat(args[0]) && !toDisplay.isEmpty() && (args[5] != null|| !args[5].trim().isEmpty())&& (args[6] != null|| !args[6].trim().isEmpty() )) {
            lblErrore.setText("");
            ObservableList<String> items = FXCollections.observableArrayList(toDisplay);
            txtOutput.setItems(items);
        }else{
           lblErrore.setText("Dati inseriti non validi");
        }

    }
}
