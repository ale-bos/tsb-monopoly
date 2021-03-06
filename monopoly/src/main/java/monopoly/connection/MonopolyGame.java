/**
 * 
 */
package monopoly.connection;

import java.io.IOException;
import java.util.List;

import monopoly.controller.PartidasController;
import monopoly.controller.UsuarioController;
import monopoly.model.Dado;
import monopoly.model.Estado.EstadoJuego;
import monopoly.model.History;
import monopoly.model.Juego;
import monopoly.model.Jugador;
import monopoly.model.JugadorHumano;
import monopoly.model.SubastaStatus;
import monopoly.model.Usuario;
import monopoly.model.tarjetas.Tarjeta;
import monopoly.model.tarjetas.TarjetaCalle;
import monopoly.model.tarjetas.TarjetaPropiedad;
import monopoly.util.GestorLogs;
import monopoly.util.StringUtils;
import monopoly.util.constantes.ConstantesMensaje;
import monopoly.util.constantes.EnumSalidaCarcel;
import monopoly.util.message.CreateAccountMessage;
import monopoly.util.message.CreateGameMessage;
import monopoly.util.message.DisconnectPlayerMessage;
import monopoly.util.message.ExceptionMessage;
import monopoly.util.message.LoginMessage;
import monopoly.util.message.game.AdvanceInBoardMessage;
import monopoly.util.message.game.BankruptcyMessage;
import monopoly.util.message.game.ChatGameMessage;
import monopoly.util.message.game.CompleteTurnMessage;
import monopoly.util.message.game.ConfirmGameReloadedMessage;
import monopoly.util.message.game.GetSavedGamesMessage;
import monopoly.util.message.game.HistoryGameMessage;
import monopoly.util.message.game.JoinGameMessage;
import monopoly.util.message.game.LoadGameMessage;
import monopoly.util.message.game.ReloadSavedGameMessage;
import monopoly.util.message.game.SaveGameMessage;
import monopoly.util.message.game.StartGameMessage;
import monopoly.util.message.game.actions.AuctionFinishMessage;
import monopoly.util.message.game.actions.AuctionPropertyMessage;
import monopoly.util.message.game.actions.BidForPropertyMessage;
import monopoly.util.message.game.actions.BidResultMessage;
import monopoly.util.message.game.actions.BuildMessage;
import monopoly.util.message.game.actions.BuyPropertyMessage;
import monopoly.util.message.game.actions.ChanceCardMessage;
import monopoly.util.message.game.actions.CommunityCardMessage;
import monopoly.util.message.game.actions.DemortgageMessage;
import monopoly.util.message.game.actions.DoubleDiceJailMessage;
import monopoly.util.message.game.actions.GoToJailMessage;
import monopoly.util.message.game.actions.MortgageMessage;
import monopoly.util.message.game.actions.PayRentMessage;
import monopoly.util.message.game.actions.PayToBankMessage;
import monopoly.util.message.game.actions.PayToLeaveJailMessage;
import monopoly.util.message.game.actions.PayToPlayerMessage;
import monopoly.util.message.game.actions.SuperTaxMessage;
import monopoly.util.message.game.actions.UnbuildMessage;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 *
 */
public class MonopolyGame extends GameServer {

	/**
	 * Creates a MonopolyGame listening on a specified port.
	 */
	public MonopolyGame(int port) throws IOException {
		super(port);
	}

	/**
	 * 
	 */
	public MonopolyGame() throws IOException {
		super();
	}

	/**
	 * When the second player connects, this method starts the game by sending
	 * the initial game state to the two players. At this time, the players'
	 * hands are null. The hands will be set when the first hand is dealt. This
	 * method also shuts down the Hub's ServerSocket so that no further players
	 * can connect.
	 */
	protected void playerConnected(int playerID) {

	}

	/**
	 * If a player disconnects, the game ends. This method shuts down the Hub,
	 * which will send a signal to the remaining connected player, if any, to
	 * let them know that their opponent has left the game. The client will
	 * respond by terminating that player's program.
	 */
	protected void playerDisconnected(int playerID) {

		GestorLogs.registrarLog(String.format(
				"El jugador \"%s\" se desconectó", playerID));

		/**
		 * Cuando se recive un mensaje de desconcexión del cliente ya sea
		 * porque cierra la ventana o porque presionó sobre
		 * "Pasar a bancarrota", se debe eliminar al jugador de NetworkPlayers y
		 * verificar si queda algún jugador humano en el juego. En caso de que
		 * no quede nadie, se debe eliminar el juego completo y parar los
		 * threads.
		 */

		// shutdownServer();
	}

	/**
	 * This is the method that responds to messages received from the clients.
	 * It handles all of the action of the game. When a message is received,
	 * this method will make any changes to the state of the game that are
	 * triggered by the message. It will then send information about the new
	 * state to each player, and it will generally send a string to each client
	 * as a message to be displayed to that player.
	 */
	protected void messageReceived(int senderId, Object message) {
		Usuario usuario;
		Juego juego;
		// JuegoController juegoController;
		Jugador jugador;
		Tarjeta tarjeta;
		History history;
		List<Juego> juegosList;
		TarjetaPropiedad tarjetaPropiedad;
		TarjetaCalle tarjetaCalle;
		ChatGameMessage msgChatGameMessage;
		StartGameMessage msgStartGameMessage;
		BankruptcyMessage msgBankruptcyMessage;
		AdvanceInBoardMessage msgAdvanceInBoard;
		BuyPropertyMessage msgBuyProperty;
		ChanceCardMessage msgChanceCard;
		CommunityCardMessage msgCommunityCard;
		PayToBankMessage msgPayToBank;
		CompleteTurnMessage msgCompleteTurnMessage;
		GoToJailMessage msgGoToJailMessage;
		PayToPlayerMessage msgPayToPlayerMessage;
		SuperTaxMessage msgSuperTax;
		DoubleDiceJailMessage msgDoubleDiceJail;
		HistoryGameMessage msgHistoryGame;
		PayToLeaveJailMessage msgPayToLeaveJail;
		SaveGameMessage msgSaveGameMessage;
		PayRentMessage msgPayRent;
		AuctionPropertyMessage msgAuctionProperty;
		AuctionFinishMessage msgAuctionFinish;
		BidForPropertyMessage msgBidForPropertyMessage;
		BidResultMessage msgBidResultMessage;
		DisconnectPlayerMessage msgDisconnectPlayerMessage;

		try {
			switch (message.getClass().getSimpleName()) {
			case ConstantesMensaje.LOGIN_MESSAGE:
				usuario = (Usuario) ((LoginMessage) message).message;
				usuario = UsuarioController.validarUsuario(
						usuario.getUserName(), usuario.getPassword());
				sendToOne(senderId, new LoginMessage(senderId, usuario));
				break;

			case ConstantesMensaje.CHAT_GAME_MESSAGE:
				msgChatGameMessage = (ChatGameMessage) message;
				history = (History) msgChatGameMessage.message;
				PartidasController.getInstance().sendChatMessage(
						msgChatGameMessage.idJuego, history);
				break;

			case ConstantesMensaje.CREATE_ACCOUNT_MESSAGE:
				usuario = (Usuario) ((CreateAccountMessage) message).message;
				UsuarioController.saveUsuario(usuario);
				sendToOne(senderId, new CreateAccountMessage(senderId, usuario));
				break;

			case ConstantesMensaje.CREATE_GAME_MESSAGE:
				usuario = (Usuario) ((CreateGameMessage) message).message;
				juego = PartidasController.getInstance()
						.crearJuego(usuario, "");
				sendToOne(senderId, new CreateGameMessage(senderId, juego));
				break;

			case ConstantesMensaje.JOIN_GAME_MESSAGE:
				jugador = (Jugador) ((JoinGameMessage) message).message;
				PartidasController.getInstance().joinPlayerGame(jugador);
				break;

			case ConstantesMensaje.BANKRUPTCY_MESSAGE:
				msgBankruptcyMessage = (BankruptcyMessage) message;
				PartidasController.getInstance().pasarABancarrota(senderId,
						msgBankruptcyMessage.UniqueIdJuego);
				break;

			case ConstantesMensaje.GET_SAVED_GAMES_MESSAGES:
				usuario = (Usuario) ((GetSavedGamesMessage) message).message;
				juegosList = PartidasController.getInstance()
						.buscarJuegosGuardados(usuario);
				sendToOne(senderId, new GetSavedGamesMessage(senderId,
						juegosList));
				break;

			case ConstantesMensaje.RELOAD_SAVED_GAME_MESSAGE:
				String nombre = (String) ((ReloadSavedGameMessage) message).juego;
				PartidasController.getInstance().reloadGame(senderId, nombre);
				break;

			case ConstantesMensaje.CONFIRM_GAME_RELOADED_MESSAGE:
				juego = (Juego) ((ConfirmGameReloadedMessage) message).juego;
				// Para debug comentar la linea siguiente, no borra el juego
				PartidasController.getInstance()
						.confirmarJuegoRestaurado(juego);
				break;

			case ConstantesMensaje.LOAD_GAME_MESSAGE:
				juego = (Juego) ((LoadGameMessage) message).message;
				PartidasController.getInstance().loadGame(senderId, juego);
				break;

			case ConstantesMensaje.SAVE_GAME_MESSAGE:
				msgSaveGameMessage = (SaveGameMessage) message;
				PartidasController.getInstance().saveGame(senderId,
						msgSaveGameMessage.uniqueIdJuego);
				break;

			case ConstantesMensaje.START_GAME_MESSAGE:
				msgStartGameMessage = (StartGameMessage) message;
				PartidasController.getInstance().establecerTurnoJugador(
						senderId, msgStartGameMessage.UniqueIdJuego,
						(Dado) msgStartGameMessage.message);
				break;

			case ConstantesMensaje.ADVANCE_IN_BOARD_MESSAGE:
				msgAdvanceInBoard = (AdvanceInBoardMessage) message;
				PartidasController.getInstance().avanzarDeCasillero(senderId,
						msgAdvanceInBoard.idJuego,
						(Dado) msgAdvanceInBoard.dados);
				break;

			case ConstantesMensaje.AUCTION_PROPERTY_MESSAGE:
				msgAuctionProperty = (AuctionPropertyMessage) message;
				PartidasController.getInstance().subastar(
						msgAuctionProperty.idJuego, senderId,
						(SubastaStatus) msgAuctionProperty.subastaStatus);
				break;

			case ConstantesMensaje.AUCTION_FINISH_MESSAGE:
				msgAuctionFinish = (AuctionFinishMessage) message;
				PartidasController.getInstance().finalizarSubasta(
						msgAuctionFinish.idJuego, senderId,
						msgAuctionFinish.monto,
						(TarjetaPropiedad) msgAuctionFinish.tarjeta);
				break;

			case ConstantesMensaje.BID_FOR_PROPERTY_MESSAGE:
				msgBidForPropertyMessage = (BidForPropertyMessage) message;
				tarjetaPropiedad = (TarjetaPropiedad) msgBidForPropertyMessage.propiedad;
				PartidasController.getInstance().ofrecerPorPropiedad(
						msgBidForPropertyMessage.idJuego,
						(JugadorHumano) msgBidForPropertyMessage.comprador,
						tarjetaPropiedad, msgBidForPropertyMessage.oferta);
				break;

			case ConstantesMensaje.BID_RESULT_MESSAGE:
				msgBidResultMessage = (BidResultMessage) message;
				PartidasController.getInstance().terminarOfertaPorPropiedad(
						msgBidResultMessage.idJuego,
						(JugadorHumano) msgBidResultMessage.comprador,
						(TarjetaPropiedad) msgBidResultMessage.propiedad,
						msgBidResultMessage.oferta.intValue(),
						msgBidResultMessage.resultado.booleanValue());
				break;

			case ConstantesMensaje.BUY_PROPERTY_MESSAGE:
				msgBuyProperty = (BuyPropertyMessage) message;
				tarjetaPropiedad = (TarjetaPropiedad) msgBuyProperty.message;
				PartidasController.getInstance().comprarPropiedad(
						msgBuyProperty.idJuego, senderId,
						tarjetaPropiedad.getNombrePropiedad());
				break;

			case ConstantesMensaje.CHANCE_CARD_MESSAGE:
				msgChanceCard = (ChanceCardMessage) message;
				tarjeta = (Tarjeta) msgChanceCard.message;
				PartidasController.getInstance().jugarTarjeta(
						msgChanceCard.idJuego, senderId, tarjeta);
				break;

			case ConstantesMensaje.COMMUNITY_CARD_MESSAGE:
				msgCommunityCard = (CommunityCardMessage) message;
				tarjeta = (Tarjeta) msgCommunityCard.message;
				PartidasController.getInstance().jugarTarjeta(
						msgCommunityCard.idJuego, senderId, tarjeta);
				break;

			case ConstantesMensaje.PAY_TO_BANK_MESSAGE:
				msgPayToBank = (PayToBankMessage) message;
				PartidasController.getInstance().pagarAlBanco(
						msgPayToBank.idJuego, senderId, msgPayToBank.monto,
						msgPayToBank.mensaje);
				break;

			case ConstantesMensaje.COMPLETE_TURN_MESSAGE:
				msgCompleteTurnMessage = (CompleteTurnMessage) message;
				PartidasController.getInstance().siguienteTurno(
						msgCompleteTurnMessage.message);
				break;

			case ConstantesMensaje.GO_TO_JAIL_MESSAGE:
				msgGoToJailMessage = (GoToJailMessage) message;
				PartidasController.getInstance().irALaCarcel(senderId,
						msgGoToJailMessage.idJuego);
				break;

			case ConstantesMensaje.PAY_TO_PLAYER_MESSAGE:
				msgPayToPlayerMessage = (PayToPlayerMessage) message;
				PartidasController.getInstance().addContadorPagos(senderId,
						msgPayToPlayerMessage.idJuego);
				break;

			case ConstantesMensaje.PAY_RENT_MESSAGE:
				msgPayRent = (PayRentMessage) message;
				PartidasController.getInstance().pagarAlquiler(senderId,
						msgPayRent.idJuego, msgPayRent.propiedadId);
				break;

			case ConstantesMensaje.SUPER_TAX_MESSAGE:
				msgSuperTax = (SuperTaxMessage) message;
				PartidasController.getInstance().impuestoAlCapital(senderId,
						msgSuperTax.idJuego, msgSuperTax.montoAPagar);
				break;

			case ConstantesMensaje.DOUBLE_DICE_JAIL_MESSAGE:
				msgDoubleDiceJail = (DoubleDiceJailMessage) message;
				PartidasController.getInstance().tirarDadosDoblesSalirCarcel(
						senderId, msgDoubleDiceJail.idJuego,
						(Dado) msgDoubleDiceJail.dados);
				break;

			case ConstantesMensaje.PAY_TO_LEAVE_JAIL_MESSAGE:
				msgPayToLeaveJail = (PayToLeaveJailMessage) message;
				PartidasController.getInstance().pagarSalidaDeCarcel(senderId,
						msgPayToLeaveJail.idJuego,
						(EnumSalidaCarcel) msgPayToLeaveJail.message);
				break;

			case ConstantesMensaje.HISTORY_GAME_MESSAGE:
				msgHistoryGame = (HistoryGameMessage) message;
				history = (History) msgHistoryGame.history;
				if (!StringUtils.IsNullOrEmpty(msgHistoryGame.idJuego))
					PartidasController.getInstance().sendHistoryGame(senderId,
							msgHistoryGame.idJuego, history);
				break;

			case ConstantesMensaje.MORTGAGE_MESSAGE:
				MortgageMessage hipoteca = ((MortgageMessage) message);
				tarjetaPropiedad = (TarjetaPropiedad) hipoteca.message;
				tarjetaPropiedad = PartidasController.getInstance()
						.hipotecarPropiedad(hipoteca.idJuego, senderId,
								tarjetaPropiedad);
				sendToOne(senderId, new MortgageMessage(senderId,
						hipoteca.idJuego, tarjetaPropiedad));
				break;

			case ConstantesMensaje.DEMORTGAGE_MESSAGE:
				DemortgageMessage deshipoteca = ((DemortgageMessage) message);
				tarjetaPropiedad = (TarjetaPropiedad) deshipoteca.message;
				tarjetaPropiedad = PartidasController.getInstance()
						.deshipotecarPropiedad(deshipoteca.idJuego, senderId,
								tarjetaPropiedad);
				sendToOne(senderId, new DemortgageMessage(senderId,
						deshipoteca.idJuego, tarjetaPropiedad));
				break;

			case ConstantesMensaje.BUILD_MESSAGE:
				BuildMessage construir = ((BuildMessage) message);
				tarjetaCalle = (TarjetaCalle) construir.message;
				int resConstruir = PartidasController.getInstance()
						.construirEdificios(construir.idJuego, senderId,
								tarjetaCalle, construir.cantidad.intValue());
				sendToOne(senderId, new BuildMessage(senderId,
						construir.idJuego, tarjetaCalle, resConstruir));
				break;

			case ConstantesMensaje.UNBUILD_MESSAGE:
				UnbuildMessage vender = ((UnbuildMessage) message);
				tarjetaCalle = (TarjetaCalle) vender.message;
				int resVender = PartidasController.getInstance()
						.venderEdificios(vender.idJuego, senderId,
								tarjetaCalle, vender.cantidad.intValue());
				sendToOne(senderId, new UnbuildMessage(senderId,
						vender.idJuego, tarjetaCalle, resVender));
				break;

			case ConstantesMensaje.DISCONNECT_MESSAGE:
				// El DisconnectMessage se recibe y trata en la clase interna
				// GameServer -> ConnectionToClient -> ReceiveThread
				// El mensaje nunca llega hasta MonopolyGame. Entonces generamos
				// un DisconnectPlayerMessage que se trate en esta clase.
				break;

			case ConstantesMensaje.DISCONNECT_PLAYER_MESSAGE:
				msgDisconnectPlayerMessage = ((DisconnectPlayerMessage) message);
				PartidasController.getInstance().desconectarJugador(senderId,
						msgDisconnectPlayerMessage.juegoId,
						msgDisconnectPlayerMessage.nombreJugador);
				break;

			case "String":
				messageString(senderId, message);
				break;

			default:
				System.out.print(message.getClass().getSimpleName());
				break;
			}
		} catch (Exception ex) {
			GestorLogs.registrarException(ex);
			sendToOne(senderId, new ExceptionMessage(ex));
		}
	}

	private void messageString(int senderId, Object message) throws Exception {
		List<Juego> juegosList;
		switch ((String) message) {
		case ConstantesMensaje.GET_PENDING_GAMES_MESSAGE:
			juegosList = PartidasController.getInstance().buscarJuegos(
					EstadoJuego.ESPERANDO_JUGADOR);
			sendToOne(senderId, new JoinGameMessage(juegosList));
			break;

		case ConstantesMensaje.THROW_DICE_TURNS_MESSAGE:

			break;

		default:
			break;

		}
	}
}
