/**
 * 
 */
package monopoly.client.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import monopoly.client.connection.ConnectionController;
import monopoly.model.History;
import monopoly.model.JugadorHumano;
import monopoly.model.SubastaStatus;
import monopoly.model.tarjetas.TarjetaPropiedad;
import monopoly.util.GestorLogs;
import monopoly.util.StringUtils;
import monopoly.util.constantes.EnumEstadoSubasta;
import monopoly.util.exception.CondicionInvalidaException;
import monopoly.util.message.game.actions.AuctionFinishMessage;
import monopoly.util.message.game.actions.AuctionPropertyMessage;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 *
 */
public class SubastaController extends AnchorPane implements Initializable {

	@FXML
	private ImageView imgPropiedad;

	@FXML
	private TableView<SubastaHistoryProperty> tblSubasta;

	@FXML
	private TableColumn<SubastaHistoryProperty, String> columnUsuario;

	@FXML
	private TableColumn<SubastaHistoryProperty, String> columnHistorico;

	@FXML
	private TextField txtMejorOferta;

	@FXML
	private TextField txtMiOferta;

	@FXML
	private Button btnPujar;

	@FXML
	private Button btnAbandonarSubasta;

	@FXML
	private Label lblMessage;

	@FXML
	private Stage currentStage;

	private List<History> historyList;

	private List<SubastaHistoryProperty> historyFilterList;

	private ObservableList<SubastaHistoryProperty> historyObservableList;

	private String idJuego;

	private JugadorHumano jugador;

	private TarjetaPropiedad tarjetaSubasta;

	private EnumEstadoSubasta estadoSubasta;

	private static SubastaController instance;

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		instance = this;
		estadoSubasta = EnumEstadoSubasta.CREADA;
		if (txtMiOferta != null) {
			txtMiOferta.lengthProperty().addListener(
					new ChangeListener<Number>() {

						@Override
						public void changed(
								ObservableValue<? extends Number> observable,
								Number oldValue, Number newValue) {

							if (newValue.intValue() > oldValue.intValue()) {
								char ch = txtMiOferta.getText().charAt(
										oldValue.intValue());
								System.out.println("Length:" + oldValue + "  "
										+ newValue + " " + ch);

								// Check if the new character is the number or
								// other's
								if (!(ch >= '0' && ch <= '9')) {

									// if it's not number then just setText to
									// previous one
									txtMiOferta.setText(txtMiOferta.getText()
											.substring(
													0,
													txtMiOferta.getText()
															.length() - 1));
								}
							}
						}

					});
		}
		historyList = new ArrayList<History>();
		historyFilterList = new ArrayList<SubastaHistoryProperty>();
		historyObservableList = FXCollections
				.observableArrayList(historyFilterList);
		configurarTabla();
	}

	private void configurarTabla() {
		// Columna Usuario
		// columnUsuario = new TableColumn<>("Usuario");
		if (columnUsuario != null)
			columnUsuario
					.setCellValueFactory(new PropertyValueFactory<SubastaHistoryProperty, String>(
							"usuario"));

		// Column Fecha
		// columnHistorico = new TableColumn<>("Fecha");
		if (columnHistorico != null)
			columnHistorico
					.setCellValueFactory(new PropertyValueFactory<SubastaHistoryProperty, String>(
							"historia"));

		// colNombre.prefWidthProperty().bind(
		// tblJuegos.widthProperty().multiply(0.30));
		// colFecha.prefWidthProperty().bind(
		// tblJuegos.widthProperty().multiply(0.15));
		// colCreador.prefWidthProperty().bind(
		// tblJuegos.widthProperty().multiply(0.25));
		// colParticipantes.prefWidthProperty().bind(
		// tblJuegos.widthProperty().multiply(0.30));

	}

	private void pujarSubasta() {
		Platform.runLater(new Runnable() {
			int cantidadOfertada = 0;
			int mejorOferta = 0;
			AuctionPropertyMessage msg;
			SubastaStatus subasta;

			@Override
			public void run() {
				try {
					lblMessage.setText("");

					if (StringUtils.IsNullOrEmpty(txtMiOferta.getText())) {
						txtMiOferta.setFocusTraversable(true);
						throw new CondicionInvalidaException(
								"Campo Mi Oferta Obligatorio.");
					}

					cantidadOfertada = Integer.parseInt(txtMiOferta.getText());
					if (jugador.getDinero() == 0
							&& estadoSubasta == EnumEstadoSubasta.CREADA) {
						subasta = new SubastaStatus(estadoSubasta, null,
								jugador, tarjetaSubasta, 1);
						msg = new AuctionPropertyMessage(idJuego, subasta);
						ConnectionController.getInstance().send(msg);
						bloquearBotones(true);
						return;
					}

					if (cantidadOfertada <= 0) {
						txtMiOferta.setFocusTraversable(true);
						throw new CondicionInvalidaException(
								"La oferta debería ser mayor a 0.");
					}

					mejorOferta = Integer.parseInt(txtMejorOferta.getText());
					if (estadoSubasta == EnumEstadoSubasta.JUGANDO) {
						if (cantidadOfertada <= mejorOferta) {
							txtMiOferta.setFocusTraversable(true);
							throw new CondicionInvalidaException(
									"Mi oferta debe superar el monto de la mejor oferta.");
						}
					}

					if (cantidadOfertada > jugador.getDinero()) {
						btnAbandonarSubasta.setFocusTraversable(true);
						throw new CondicionInvalidaException(
								"No tienes suficiente dinero para ofertar. Debes abandonar la subasta.");
					}

					bloquearBotones(true);
					subasta = new SubastaStatus(estadoSubasta, null, jugador,
							tarjetaSubasta, cantidadOfertada);
					msg = new AuctionPropertyMessage(idJuego, subasta);
					ConnectionController.getInstance().send(msg);

					lblMessage.setText("Esperando por apuestas...");

				} catch (CondicionInvalidaException ce) {
					lblMessage.setText(ce.getMessage());
				} catch (Exception ex) {

				}
			}
		});

	}

	private void bloquearBotones(boolean bHabilitado) {
		try {
			btnPujar.setDisable(!bHabilitado);
			btnAbandonarSubasta.setDisable(!bHabilitado);
		} catch (Exception ex) {
			GestorLogs.registrarError(ex);
		}
	}

	private void abandonarSubasta() {
		Platform.runLater(new Runnable() {
			AuctionFinishMessage msg;

			@Override
			public void run() {
				try {
					if (TableroController.getInstance().showYesNoMsgBox("Abandonar Subasta", null, "¿Está seguro que desea abandonar la subasta?") == ButtonType.YES) {
						bloquearBotones(true);
						lblMessage.setText("La pantalla seguirá activa hasta que finalice la subasta.");
						msg = new AuctionFinishMessage(idJuego,
								"Abandonar Subasta.");
						ConnectionController.getInstance().send(msg);
					}
				} catch (Exception ex) {
					GestorLogs.registrarError(ex);
				}
			}
		});
	}

	public void actualizarSubasta(SubastaStatus status) {
		try {
			Platform.runLater(new Runnable() {

				@Override
				public void run() {
					try {
						estadoSubasta = status.estado;
						if(status.estado== EnumEstadoSubasta.JUGANDO)
						{
							for (History history : status.historyList) {
								agregarHistoriaDeSubasta(history);
							}
							
							if(status.jugadorActual.getNombre().equals(jugador.getNombre()))
							{
								bloquearBotones(false);
								txtMejorOferta.setText(String.valueOf(status.montoSubasta));
							}
						}
					} catch (Exception ex) {
						GestorLogs.registrarException(ex);
					}
				}
			});
		} catch (Exception ex) {
			GestorLogs.registrarException(ex);
		}
	}

	public void agregarHistoriaDeSubasta(final History history) {
		FutureTask<Void> taskAddHistory = null;
		try {
			taskAddHistory = new FutureTask<Void>(new Callable<Void>() {
				@Override
				public Void call() throws Exception {

					historyList.add(history);
					historyFilterList.add(new SubastaHistoryProperty(history
							.getUsuario(), history.getMensaje()));

					historyObservableList = FXCollections
							.observableArrayList(historyFilterList);

					if (tblSubasta != null) {
						tblSubasta.getItems().clear();
						tblSubasta.setItems(historyObservableList);
						// tblSubasta.getColumns().setAll(columnUsuario,
						// columnHistorico);
					}
					return null;
				}
			});
			Platform.runLater(taskAddHistory);
			taskAddHistory.get();

		} catch (Exception ex) {
			GestorLogs.registrarException(ex);
		}
	}

	@FXML
	void processPujar(ActionEvent event) {
		try {
			pujarSubasta();

		} catch (NumberFormatException nfe) {
			lblMessage.setText("La oferta debe ser un valor númerico.");
		} catch (Exception ex) {
			lblMessage.setText(ex.getMessage());
		}
	}

	@FXML
	void processDejarSubasta(ActionEvent event) {

		try {
			abandonarSubasta();
		} catch (Exception ex) {
			lblMessage.setText(ex.getMessage());
		}
	}

	public TarjetaPropiedad getTarjetaSubasta() {
		return tarjetaSubasta;
	}

	public void setTarjetaSubasta(TarjetaPropiedad tarjetaSubasta) {
		this.tarjetaSubasta = tarjetaSubasta;
	}

	public static SubastaController getInstance() {
		return instance;
	}

	public Stage getCurrentStage() {
		return currentStage;
	}

	public void setCurrentStage(Stage currentStage) {
		this.currentStage = currentStage;
	}

	public String getIdJuego() {
		return idJuego;
	}

	public void setIdJuego(String idJuego) {
		this.idJuego = idJuego;
	}

	public JugadorHumano getJugador() {
		return jugador;
	}

	public void setJugador(JugadorHumano jugador) {
		this.jugador = jugador;
	}

	public EnumEstadoSubasta getEstadoSubasta() {
		return estadoSubasta;
	}

	public void setEstadoSubasta(EnumEstadoSubasta estadoSubasta) {
		this.estadoSubasta = estadoSubasta;
	}

	public static class SubastaHistoryProperty {
		private final SimpleObjectProperty<String> usuario;
		private final SimpleObjectProperty<String> historia;

		public SubastaHistoryProperty(String usuario, String historia) {
			this.usuario = new SimpleObjectProperty<String>(usuario);
			this.historia = new SimpleObjectProperty<String>(historia);
		}

		public SimpleObjectProperty<String> getUsuario() {
			return usuario;
		}

		public SimpleObjectProperty<String> getHistoria() {
			return historia;
		}
	}

}