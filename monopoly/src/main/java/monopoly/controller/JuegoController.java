package monopoly.controller;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import monopoly.dao.IJuegoDao;
import monopoly.model.AccionEnCasillero;
import monopoly.model.AccionEnTarjeta;
import monopoly.model.Dado;
import monopoly.model.Estado;
import monopoly.model.Estado.EstadoJuego;
import monopoly.model.Estado.EstadoJugador;
import monopoly.model.History;
import monopoly.model.Juego;
import monopoly.model.Jugador;
import monopoly.model.JugadorHumano;
import monopoly.model.JugadorVirtual;
import monopoly.model.MonopolyGameStatus;
import monopoly.model.SubastaStatus;
import monopoly.model.Usuario;
import monopoly.model.tablero.Casillero;
import monopoly.model.tablero.CasilleroCalle;
import monopoly.model.tablero.CasilleroCompania;
import monopoly.model.tablero.CasilleroEstacion;
import monopoly.model.tarjetas.Tarjeta;
import monopoly.model.tarjetas.TarjetaCalle;
import monopoly.model.tarjetas.TarjetaCompania;
import monopoly.model.tarjetas.TarjetaComunidad;
import monopoly.model.tarjetas.TarjetaEstacion;
import monopoly.model.tarjetas.TarjetaPropiedad;
import monopoly.model.tarjetas.TarjetaSuerte;
import monopoly.util.GestorLogs;
import monopoly.util.StringUtils;
import monopoly.util.constantes.ConstantesMensaje;
import monopoly.util.constantes.EnumAction;
import monopoly.util.constantes.EnumEstadoSubasta;
import monopoly.util.constantes.EnumSalidaCarcel;
import monopoly.util.exception.CondicionInvalidaException;
import monopoly.util.exception.SinDineroException;
import monopoly.util.exception.SinEdificiosException;
import monopoly.util.message.ExceptionMessage;
import monopoly.util.message.game.BankruptcyMessage;
import monopoly.util.message.game.ChatGameMessage;
import monopoly.util.message.game.CompleteTurnMessage;
import monopoly.util.message.game.HistoryGameMessage;
import monopoly.util.message.game.ReloadSavedGameMessage;
import monopoly.util.message.game.actions.AuctionDecideMessage;
import monopoly.util.message.game.actions.AuctionFinishMessage;
import monopoly.util.message.game.actions.AuctionNotifyMessage;
import monopoly.util.message.game.actions.AuctionPropertyMessage;
import monopoly.util.message.game.actions.BidForPropertyMessage;
import monopoly.util.message.game.actions.BidResultMessage;
import monopoly.util.message.game.actions.PayToPlayerMessage;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 * 
 */
public class JuegoController implements Serializable {

	private static final long serialVersionUID = 7433262560591847582L;

	static ApplicationContext appContext = new ClassPathXmlApplicationContext(
			"spring/config/BeanLocations.xml");

	private Juego juego;

	private Estado estadoJuego;

	private int cantJugadores;

	private BancoController gestorBanco;

	private TableroController gestorTablero;

	private JugadorController gestorJugadores;

	private JugadorVirtualController gestorJugadoresVirtuales;

	private MonopolyGameStatus status;

	private SubastaController gestorSubasta;

	private int contadorPagos;

	public final int tiempoDeEspera = 2000;

	/**
	 * Para pruebas, hace al creador del juego dueño de todas las propiedades.
	 * Usado para probar la funcionalidad de bancarrota.
	 */
	private static final boolean BANCARROTA_TESTING = false;

	public JuegoController(Usuario creador, String nombre) {
		this.gestorTablero = new TableroController();
		this.gestorBanco = new BancoController(gestorTablero.getTablero()
				.getCasillerosList());
		this.juego = new Juego(creador, nombre);
		this.juego.setTablero(gestorTablero.getTablero());
		this.estadoJuego = new Estado(EstadoJuego.CREADO);
		this.gestorJugadores = new JugadorController();
		this.gestorJugadoresVirtuales = new JugadorVirtualController();
		this.contadorPagos = 0;
	}

	/**
	 * ¡¡¡ OJO CON ESTO !!!<br>
	 * Hace al jugador {@code owner} dueño de <b>TODAS</b> las propiedades del
	 * juego, antes de arrancar el juego. Se usa solo para <b>TESTING</b>.
	 * 
	 * @param owner
	 *            El jugador que va a ser dueño de TODAS las propiedade
	 */
	private void make_owner(Jugador owner) {
		// Jugador jugador = this.getJugadorHumano(owner);

		for (Casillero casillero : this.gestorTablero.getTablero()
				.getCasillerosList()) {
			switch (casillero.getTipoCasillero()) {
			case C_CALLE:
				owner.adquirirPropiedad(((CasilleroCalle) casillero)
						.getTarjetaCalle());
				break;
			case C_COMPANIA:
				owner.adquirirPropiedad(((CasilleroCompania) casillero)
						.getTarjetaCompania());
				break;
			case C_ESTACION:
				owner.adquirirPropiedad(((CasilleroEstacion) casillero)
						.getTarjetaEstacion());
				break;
			default:
				break;
			}
		}
		owner.setDinero(100000);
	}

	/**
	 * Retorna el JugadorHumano asociado a un usuario
	 * 
	 * @param usuario
	 *            El usuario que se quiere buscar
	 * @return El JugadorHumano de ese usuario
	 */
	public JugadorHumano getJugadorHumano(Usuario usuario) {
		for (JugadorHumano jugador : this.getGestorJugadores()
				.getListaJugadoresHumanos()) {
			if (jugador.getUsuario().getUserName()
					.equals(usuario.getUserName()))
				return jugador;
		}
		return null;
	}

	/**
	 * 
	 * Método que agrega un jugador al juego, informa al resto de los jugadores
	 * de qué jugador se unió al juego, valida de que se haya completado la
	 * cantidad de jugadores especificados por el creador y establece los
	 * turnos.
	 * 
	 * @param jugador
	 *            El jugador que se va a agregar al Juego
	 */
	@SuppressWarnings("unused")
	public void addPlayer(Jugador jugador) throws Exception {
		this.gestorJugadores.addPlayer(jugador);
		this.juego.addJugador(jugador);
		jugador.setCasilleroActual(gestorTablero.getCasillero(1));

		History history = new History(StringUtils.getFechaActual(),
				jugador.getNombre(), "Se unió al juego.");
		HistoryGameMessage msg = new HistoryGameMessage(history);
		if (jugador.isHumano())
			sendToOther(((JugadorHumano) jugador).getSenderID(), msg);

		if (this.gestorJugadores.cantJugadoresConectados() == cantJugadores) {
			estadoJuego.actualizarEstadoJuego();
			gestorJugadores.establecerTurnos();
		}

		// Para pruebas, hace al creador del juego dueño de todas las
		// propiedades. Usado para probar la funcionalidad de bancarrota.
		if (BANCARROTA_TESTING
				&& jugador.isHumano()
				&& ((JugadorHumano) jugador).getUsuario().equals(
						juego.getOwner()))
			make_owner(jugador);
	}

	/**
	 * Restaura un juego serializado.
	 * 
	 * @throws IOException
	 *             Si no se encuentra el juego.
	 */
	public void reloadGame(int senderID) throws Exception {

		for (JugadorHumano jugador : this.gestorJugadores
				.getListaJugadoresHumanos()) {
			jugador.setSenderID(senderID);
			this.gestorJugadores.cleanNetworkPlayers();
			this.gestorJugadores.addNetworkPlayer(jugador);
		}

		List<History> historia = new ArrayList<History>();
		historia.add(new History(StringUtils.getFechaActual(), gestorJugadores
				.getCurrentPlayer().getNombre(), "Restauró el juego."));
		MonopolyGameStatus status = new MonopolyGameStatus(
				gestorJugadores.getTurnoslist(), gestorBanco.getBanco(),
				gestorTablero.getTablero(), EstadoJuego.TIRAR_DADO, null,
				gestorJugadores.getCurrentPlayer(), historia, null);
		sendToOne(senderID,
				new ReloadSavedGameMessage(senderID, this.getJuego(), status));
	}

	/**
	 * 
	 * Método que establece la suma de dados obtenidos en la tirada inicial para
	 * establecer el turno de inicio del juego. Luego valida de que todos los
	 * participantes hayan arrojado sus dados para establecer el orden de
	 * turnos.
	 * 
	 * @param key
	 *            id de conexión del jugador humano conectado al juego.
	 * @param dados
	 *            objecto dado con los números obtenidos.
	 */
	public void establecerTurnoJugador(int key, Dado dados) throws Exception {
		JugadorHumano jugador = gestorJugadores.getJugadorHumano(key);
		jugador.setTiradaInicial(dados);
		boolean tiraronTodosDados = true;
		History history;

		for (Jugador j : gestorJugadores.getListaJugadoresHumanos()) {
			if (j.getTiradaInicial() == null) {
				tiraronTodosDados = false;
				break;
			}
		}
		if (tiraronTodosDados) {
			estadoJuego.actualizarEstadoJuego();
			ordenarTurnos();

			List<History> historyList = new ArrayList<History>();
			for (int i = 0; i < gestorJugadores.getTurnoslist().size(); i++) {
				history = new History(StringUtils.getFechaActual(),
						gestorJugadores.getTurnoslist().get(i).getNombre(),
						(i + 1)
								+ "°"
								+ " Orden en la ronda. Suma de dados: "
								+ gestorJugadores.getTurnoslist().get(i)
										.getTiradaInicial().getSuma());
				historyList.add(history);
			}

			history = new History(StringUtils.getFechaActual(), gestorJugadores
					.getCurrentPlayer().getNombre(),
					"Turno para tirar los dados.");
			historyList.add(history);

			if (gestorJugadores.getCurrentPlayer().isVirtual()) {
				status = new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ESPERANDO_TURNO, null,
						gestorJugadores.getCurrentPlayer(), historyList, null);
				sendToAll(status);
				avanzarDeCasilleroJV();
			} else {
				JugadorHumano jh = (JugadorHumano) gestorJugadores
						.getCurrentPlayer();
				status = new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.TIRAR_DADO, null,
						gestorJugadores.getCurrentPlayer(), historyList, null);
				sendToOne(jh.getSenderID(), status);

				status = new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ESPERANDO_TURNO, null,
						gestorJugadores.getCurrentPlayer(), historyList, null);
				sendToOther(jh.getSenderID(), status);
			}

		}
	}

	/**
	 * 
	 * Método para ordenar la lista cicular de jugadores para establecer el
	 * turno de los mismos en base a los resultados de los dados arrojados. Una
	 * vez ordenados informa a los jugadores el orden establecido.
	 */
	private void ordenarTurnos() throws Exception {
		this.gestorJugadores.ordenarTurnos();

		for (Jugador jug : gestorJugadores.getTurnoslist()) {
			for (Casillero casillero : gestorTablero.getTablero()
					.getCasillerosList()) {

				if (jug.getCasilleroActual().equals(casillero)) {
					casillero.addJugador(jug);
					break;
				}
			}
		}
	}

	/**
	 * Pasa a un Jugador al estado de bancarrota.
	 * 
	 * @see #pasarABancarrota(Jugador)
	 * @param senderID
	 *            El ID del jugador que se pasa a bancarrota
	 */
	public void pasarABancarrota(int senderID) {
		JugadorHumano jugador = gestorJugadores.getJugadorHumano(senderID);
		pasarABancarrota(jugador);
	}

	/**
	 * Pasa a un Jugador al estado de bancarrota. Ejecuta las siguientes
	 * acciones:
	 * <ol>
	 * <li>Pone al jugador en estado de BANCARROTA y lo saca del listado de
	 * turnos.</li>
	 * <li>Recorre las propiedades y vende los edificios (si tienen)</li>
	 * <li>Pasa todas las propiedades al banco y deshipoteca las que estén
	 * hipotecadas.</li>
	 * <li>Si el jugador es Humano, desconecta la sesión.</li>
	 * </ol>
	 * 
	 * @param jugador
	 *            El jugador que se quiere pasar a bancarrota
	 */
	public void pasarABancarrota(Jugador jugador) {
		List<History> historyList = new ArrayList<History>();
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		MonopolyGameStatus status;
		String mensaje;
		List<Jugador> turnosList;

		TarjetaCalle pCalle;
		Jugador player = gestorJugadores.getPlayerFromTurnosList(jugador
				.getNombre());

		// 1. Ponemos al Jugador en estado de bancarrota
		player.setEstadoJugador(EstadoJugador.EJ_BANCARROTA);

		// 2. Eliminamos los edificios que tiene el jugador.
		for (TarjetaPropiedad propiedad : player.getTarjPropiedadList()) {
			if (propiedad.isPropiedadCalle()) {
				pCalle = (TarjetaCalle) propiedad;
				if (pCalle.getNroCasas() > 0) {
					if (pCalle.getNroCasas() == 5) {
						gestorBanco.getBanco().addHoteles(1);
					} else {
						gestorBanco.getBanco().addCasas(pCalle.getNroCasas());
					}
					pCalle.getCasillero().setNroCasas(0);
				}

			}
			// 3. Deshipotemos la propiedad si estaba en ese estado
			propiedad.setHipotecada(false);
			// 4. Volvemos la propiedad al banco
			propiedad.setJugador(null);
		}

		// 5. Eliminamos las propiedades del jugador
		player.getTarjPropiedadList().clear();

		// 6. Volvemos las tarjetas de "libre de carcel" a los pozos
		for (Tarjeta tarjetaCarcel : player.getTarjetaCarcelList()) {
			gestorTablero.getGestorTarjetas().agregarTarjetaLibreDeCarcel(
					tarjetaCarcel);
		}

		// 7. Eliminamos al jugador de la lista de jugadores
		gestorJugadores.removePlayerFromTurnos(player);
		cantJugadores--;

		// 8. Si es un jugador humano, lo eliminamos de la lista de
		// NetworkPlayer
		if (player.isHumano())
			gestorJugadores.removeNetworkPlayer((JugadorHumano) player);

		// Informamos a todos los jugadores de la bancarrota...
		mensaje = String.format(
				"El jugador '%s' se declaró en bancarrota y salió del Juego.",
				player.getNombre());

		// ... y actualizamos el estado del juego
		historyList.add(new History(StringUtils.getFechaActual(), player
				.getNombre(), "Se declaró en bancarrota y salío del Juego"));
		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		try {
			sendToAll(new BankruptcyMessage(this.getJuego().getUniqueID(),
					mensaje));
			sendToAll(status);
		} catch (Exception e) {
			GestorLogs.registrarError(e);
			e.printStackTrace();
		}

		this.verificarEstadoJuego();
	}

	/**
	 * Verifica la cantidad de jugadores humanos y virtuales que están Jugando y
	 * toma acciones al respecto. Si solo queda un humano y ningún virtual, le
	 * manda un mensaje informando que es el ganador. Si no quedan más humanos,
	 * termina el juego. En cualquier otro caso no hace nada.
	 * 
	 * @return true si realizó alguna acción. false en caso contrario.
	 */
	private boolean verificarEstadoJuego() {
		int cantJHumanos = 0;
		int cantJVirtuales = 0;
		Jugador lastPlayer = null;

		List<Jugador> jugadores = gestorJugadores.getTurnoslist();

		for (Jugador jugador : jugadores) {
			if (jugador.isHumano()) {
				cantJHumanos++;
				lastPlayer = jugador;
			} else
				cantJVirtuales++;
		}

		if (cantJHumanos == 1 && cantJVirtuales == 0) {
			// Enviar mensaje informando que ganó
			try {
				GestorLogs
						.registrarLog(String.format(
								"El jugador %s ganó el juego %s.", lastPlayer
										.getNombre(), this.getJuego()
										.getNombreJuego()));
				sendToAll(ConstantesMensaje.WIN_MESSAGE);
				return true;
			} catch (Exception e) {
				GestorLogs.registrarError(e);
				e.printStackTrace();
			}
		}
		if (cantJHumanos == 0) {
			// terminar el juego
			PartidasController.getInstance().removeJuego(this);
			return true;
		}
		return false;
	}

	/**
	 * Informa de la desconexión de un jugador y verifica el estado del juego
	 * 
	 * @param senderID
	 *            El id del jugador que se desconectó.
	 */
	public void desconectarJugador(int senderID, String nombreJugador) {
		// JugadorHumano jugador = gestorJugadores.getJugadorHumano(senderID);
		GestorLogs.registrarLog(String.format(
				"El jugador %s se salió del juego %s.", nombreJugador, this
						.getJuego().getNombreJuego()));
		verificarEstadoJuego();
	}

	/**
	 * Método para avanzar de casillero en base al suma de datos que arrojó el
	 * jugador.
	 * 
	 * @param senderId
	 *            Id de conexión de un jugador humano.
	 * @param dados
	 *            dados arrojados en la tirada.
	 * 
	 * @throws CondicionInvalidaException
	 * @throws Exception
	 */
	public void avanzarDeCasillero(int senderId, Dado dados)
			throws CondicionInvalidaException, Exception {
		Jugador jugador;
		Casillero casillero;
		MutableBoolean cobraSalida = new MutableBoolean(true);
		AccionEnCasillero accion;

		jugador = gestorJugadores.getJugadorHumano(senderId);
		jugador.setUltimoResultado(dados);
		casillero = gestorTablero.moverAdelante(jugador, dados.getSuma(),
				cobraSalida);

		if (cobraSalida.booleanValue())
			gestorBanco.pagarPasoSalida(jugador);

		accion = gestorTablero.getAccionEnCasillero(jugador, casillero);

		jugarAccionCasillero(accion, jugador, casillero, senderId);
	}

	/**
	 * Método utilizado para realizar la acción correspondiente al casillero en
	 * que un jugador humano cayó.
	 * 
	 * @param accion
	 *            Acción a realizar según el casillero que cayó.
	 * @param jugador
	 *            a realizar la acción.
	 * @param casillero
	 *            al que se avanzó.
	 * @param senderId
	 *            Id de conexión de un jugador humano.
	 * 
	 * @throws CondicionInvalidaException
	 * @throws SinDineroException
	 * @throws Exception
	 */
	private void jugarAccionCasillero(AccionEnCasillero accion,
			Jugador jugador, Casillero casillero, int senderId)
			throws CondicionInvalidaException, SinDineroException, Exception {

		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.JUGANDO;
		EstadoJuego estadoJuegoRestoJugadores = EstadoJuego.ESPERANDO_TURNO;
		MonopolyGameStatus status;
		Tarjeta tarjetaSelected = null;
		String mensaje;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();

		switch (accion.getAccion()) {
		case TARJETA_SUERTE:
			tarjetaSelected = gestorTablero.getTarjetaSuerte();
			accion.setMonto(((TarjetaSuerte) tarjetaSelected).getIdTarjeta());
			break;
		case TARJETA_COMUNIDAD:
			tarjetaSelected = gestorTablero.getTarjetaComunidad();
			accion.setMonto(((TarjetaComunidad) tarjetaSelected).getIdTarjeta());
			break;
		case DISPONIBLE_PARA_VENDER:
		case PAGAR_ALQUILER:
		case IMPUESTO_DE_LUJO:
		case IMPUESTO_SOBRE_CAPITAL:
		case IR_A_LA_CARCEL:
		case DESCANSO:
		case HIPOTECADA:
		case MI_PROPIEDAD:
			break;

		default:
			throw new CondicionInvalidaException(String.format(
					"La acción %s es inválida.", accion.toString()));
		}

		mensaje = String.format("Avanzó al casillero %s, %s",
				casillero.getNombreCasillero(), accion.getMensaje());

		historyList.add(new History(StringUtils.getFechaActual(), jugador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, accion,
				gestorJugadores.getCurrentPlayer(), historyList,
				tarjetaSelected);
		status.setMensajeAux(accion.getMensaje());
		status.setMonto(accion.getMonto());
		sendToOne(senderId, status);

		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoRestoJugadores, accion,
				gestorJugadores.getCurrentPlayer(), historyList,
				tarjetaSelected);
		sendToOther(senderId, status);
	}

	/**
	 * Método utilizado por un jugador virtual para avanzar de casillero.
	 * 
	 * @throws Exception
	 */
	public void avanzarDeCasilleroJV() throws Exception {
		AccionEnCasillero accion;
		MonopolyGameStatus status;
		Tarjeta tarjeta;
		Dado dados = null;
		Casillero casillero;
		History history;
		List<History> historyList;
		JugadorVirtual jugadorActual = (JugadorVirtual) this.gestorJugadores
				.getCurrentPlayer();
		String mensaje = "";
		MutableBoolean cobraSalida = new MutableBoolean(true);

		mensaje = gestorJugadoresVirtuales.deshipotecarAleatorio(jugadorActual);

		if (!StringUtils.IsNullOrEmpty(mensaje)) {
			sendToAll(new HistoryGameMessage(new History(
					StringUtils.getFechaActual(), gestorJugadores
							.getCurrentPlayer().getNombre(), mensaje)));
		}

		mensaje = "";

		try {
			mensaje = gestorJugadoresVirtuales
					.construirAleatorio(jugadorActual);

		} catch (SinEdificiosException e) {
			mensaje = "No pudo comprar edificios porque no tiene disponibilidad en el banco";
		} catch (SinDineroException e) {
			mensaje = "No pudo comprar edificios porque no tiene dinero suficiente";
		}

		if (!StringUtils.IsNullOrEmpty(mensaje)) {
			sendToAll(new HistoryGameMessage(new History(
					StringUtils.getFechaActual(), gestorJugadores
							.getCurrentPlayer().getNombre(), mensaje)));
		}

		try {
			// ~~~> pregunto si el jugador está preso.
			if (jugadorActual.estaPreso()) {
				// ~~~> Si el jugador está preso pregunto si decide salir con
				// una tarjeta.
				if (jugadorActual.getTarjetaCarcelList().size() > 0
						&& gestorJugadoresVirtuales
								.decidirSalirTarjeta(jugadorActual)) {
					tarjeta = jugadorActual.getTarjetaCarcelList().get(0);
					jugadorActual.getTarjetaCarcelList().remove(tarjeta);
					gestorTablero.getGestorTarjetas()
							.agregarTarjetaLibreDeCarcel(tarjeta);
					mensaje = String
							.format("Usó una tarjeta de la %s para salir de la cárcel.",
									tarjeta.isTarjetaSuerte() ? "Suerte"
											: "Caja de la Comunidad");
				} else {
					// ~~~> Si el jugador no decide usar la tarjeta por no
					// poseer una o no le conviene.
					// ~~~> Pregunto si quiere salir de la cárcel pagando.
					if (gestorJugadoresVirtuales
							.decidirSalirPagando(jugadorActual)) {
						jugadorActual.pagar(50);
						jugadorActual.setPreso(false);
						mensaje = String.format(
								"Pagó %s para salir de la cárcel.",
								StringUtils.formatearAMoneda(50));
					} else {
						dados = new Dado();
						tirarDadosDoblesSalirCarcel(jugadorActual, dados);
						return;
					}
				}
			}

			if (!StringUtils.IsNullOrEmpty(mensaje)) {
				history = new History(StringUtils.getFechaActual(),
						jugadorActual.getNombre(), mensaje);
				sendToAll(new HistoryGameMessage(history));
			}

			dados = new Dado();
			jugadorActual.setUltimoResultado(dados);

			casillero = gestorTablero.moverAdelante(jugadorActual,
					dados.getSuma(), cobraSalida);

			if (cobraSalida.booleanValue())
				gestorBanco.pagarPasoSalida(jugadorActual);

			mensaje = String.format("Avanzó %s casilleros hasta %s.",
					dados.getSuma(), casillero.getNombreCasillero());

			history = new History(StringUtils.getFechaActual(),
					jugadorActual.getNombre(), mensaje);
			historyList = new ArrayList<History>();
			historyList.add(history);

			status = new MonopolyGameStatus(gestorJugadores.getTurnoslist(),
					gestorBanco.getBanco(), gestorTablero.getTablero(),
					EstadoJuego.ESPERANDO_TURNO, null, jugadorActual,
					historyList, null);
			sendToAll(status);

			accion = gestorTablero.getAccionEnCasillero(jugadorActual,
					casillero);

			sendToAll(new HistoryGameMessage(new History(
					StringUtils.getFechaActual(), jugadorActual.getNombre(),
					accion.getMensaje())));

			if (jugarAccionEnCasilleroJV(accion, jugadorActual, casillero))
				siguienteTurno(true);

		} catch (CondicionInvalidaException | SinDineroException e) {
			/*
			 * "SinDineroException" no debería generarse nunca para un
			 * JugadorVirtual (como sería en este caso), pero como el método
			 * "gestorTablero.comprarPropiedad()" es genérico para Jugador,
			 * entonces tenemos que "catchar" la excepción.
			 */
			GestorLogs.registrarError(e);
			e.printStackTrace();
		} catch (Exception e) {
			GestorLogs.registrarError(e);
			e.printStackTrace();
		}
	}

	/**
	 * Método utilizado para realizar la acción correspondiente al casillero en
	 * que un jugador cayó.
	 * 
	 * @param accion
	 *            accion Acción a realizar según el casillero que cayó.
	 * @param jugador
	 *            jugador jugador a realizar la acción.
	 * @param casillero
	 *            al que se avanzó.
	 * 
	 * @throws CondicionInvalidaException
	 * @throws SinDineroException
	 * @throws Exception
	 */
	private boolean jugarAccionEnCasilleroJV(AccionEnCasillero accion,
			JugadorVirtual jugador, Casillero casillero)
			throws CondicionInvalidaException, SinDineroException, Exception {

		Tarjeta tarjetaSelected = null;
		TarjetaPropiedad tarjetaPropiedad = null;
		CasilleroCalle casilleroCalle;
		CasilleroEstacion casilleroEstacion;
		CasilleroCompania casilleroCompania;
		SubastaStatus subastaStatus;
		Jugador duenio;
		String mensaje;
		int montoAPagar;

		// Traigo el casillero del tablero...
		// casillero =
		// gestorTablero.getCasillero(casillero.getNumeroCasillero());

		switch (accion.getAccion()) {
		case TARJETA_SUERTE:
			tarjetaSelected = gestorTablero.getTarjetaSuerte();
			realizarObjetivoTarjeta(jugador, tarjetaSelected);
			break;
		case TARJETA_COMUNIDAD:
			tarjetaSelected = gestorTablero.getTarjetaComunidad();
			realizarObjetivoTarjeta(jugador, tarjetaSelected);
			break;
		case DISPONIBLE_PARA_VENDER:

			switch (casillero.getTipoCasillero()) {
			case C_CALLE:
				tarjetaPropiedad = ((CasilleroCalle) casillero)
						.getTarjetaCalle();
				break;
			case C_ESTACION:
				tarjetaPropiedad = ((CasilleroEstacion) casillero)
						.getTarjetaEstacion();
				break;
			case C_COMPANIA:
				tarjetaPropiedad = ((CasilleroCompania) casillero)
						.getTarjetaCompania();
				break;
			default:
				break;
			}

			if (gestorJugadoresVirtuales.decidirComprar(casillero, jugador)) {
				comprarPropiedad(jugador, tarjetaPropiedad);
			} else {
				mensaje = String.format(
						"Decidió no comprar. Se subasta la propiedad %s.",
						casillero.getNombreCasillero());
				sendToAll(new HistoryGameMessage(new History(
						StringUtils.getFechaActual(), jugador.getNombre(),
						mensaje)));

				Thread.sleep(1500);
				montoAPagar = (int) (tarjetaPropiedad.getValorPropiedad() * 0.1);
				if (gestorJugadoresVirtuales.decidirAceptarSubasta(
						tarjetaPropiedad, montoAPagar, jugador)) {
					subastaStatus = new SubastaStatus(EnumEstadoSubasta.CREADA,
							null, jugador, tarjetaPropiedad, montoAPagar);
				} else {
					subastaStatus = new SubastaStatus(EnumEstadoSubasta.CREADA,
							null, null, tarjetaPropiedad, montoAPagar);
				}

				subastar(jugador, subastaStatus);
				return false;
			}
			break;
		case IMPUESTO_DE_LUJO:
			jugador.pagar(100);
			break;
		case IMPUESTO_SOBRE_CAPITAL:
			jugador.pagar(gestorJugadoresVirtuales
					.decidirImpuestoEspecial(jugador));
			break;
		case IR_A_LA_CARCEL:
			gestorTablero.irACarcel(jugador);
			break;
		case PAGAR_ALQUILER:
			switch (casillero.getTipoCasillero()) {
			case C_CALLE:
				casilleroCalle = (CasilleroCalle) casillero;
				tarjetaPropiedad = casilleroCalle.getTarjetaCalle();
				montoAPagar = casilleroCalle.getTarjetaCalle()
						.calcularAlquiler();
				break;
			case C_ESTACION:
				casilleroEstacion = (CasilleroEstacion) casillero;
				tarjetaPropiedad = casilleroEstacion.getTarjetaEstacion();
				montoAPagar = casilleroEstacion.getTarjetaEstacion()
						.calcularAlquiler();
				break;
			case C_COMPANIA:
				casilleroCompania = (CasilleroCompania) casillero;
				tarjetaPropiedad = casilleroCompania.getTarjetaCompania();
				montoAPagar = casilleroCompania.getTarjetaCompania()
						.calcularAlquiler(jugador.getUltimoResultado());
				break;
			default:
				montoAPagar = 0;
				break;
			}
			duenio = tarjetaPropiedad.getJugador();

			gestorJugadoresVirtuales
					.pagarAJugador(jugador, duenio, montoAPagar);

			break;
		case DESCANSO:
		case HIPOTECADA:
		case MI_PROPIEDAD:
			break;
		default:
			throw new CondicionInvalidaException(String.format(
					"La acción %s es inválida.", accion.toString()));
		}
		return true;
	}

	/**
	 * Método para determinar el siguiente turno de un jugador.
	 * 
	 * @param validaDadosDobles
	 * @throws Exception
	 */
	public void siguienteTurno(boolean validaDadosDobles) throws Exception {
		History history;
		Jugador jugadorActual;
		Jugador jugadorSiguiente;
		MonopolyGameStatus gameStatus;
		int senderId;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();
		EstadoJuego estadoJuego;

		jugadorActual = gestorJugadores.getCurrentPlayer();

		// ~~> Válida q haya sacado dobles y si está habilitado
		// para tirar de nuevo en caso q sean dobles.
		if (validaDadosDobles && jugadorActual.tiroDobles()) {

			// ~~~> Sacó dobles, incremento el contador.
			jugadorActual.incrementarCantidadDadosDobles();

			// ~~~> Si es la tercera vez q saca dobles, va a la cárcel.
			if (jugadorActual.getCatidadDadosDobles() < 3) {
				history = new History(StringUtils.getFechaActual(),
						jugadorActual.getNombre(), String.format(
								"Juega otro turno por sacar dados dobles %s",
								jugadorActual.getParDados()));
				historyList.add(history);

				// ~~~> Envío los mensajes a todos los clientes
				if (jugadorActual.isVirtual()) {
					gameStatus = new MonopolyGameStatus(
							gestorJugadores.getTurnoslist(),
							gestorBanco.getBanco(), gestorTablero.getTablero(),
							EstadoJuego.ESPERANDO_TURNO, null, jugadorActual,
							historyList, null);
					sendToAll(gameStatus);
					avanzarDeCasilleroJV();
				} else {
					senderId = ((JugadorHumano) jugadorActual).getSenderID();
					gameStatus = new MonopolyGameStatus(
							gestorJugadores.getTurnoslist(),
							gestorBanco.getBanco(), gestorTablero.getTablero(),
							EstadoJuego.DADOS_DOBLES, null, jugadorActual,
							historyList, null);
					sendToOne(senderId, gameStatus);

					gameStatus = new MonopolyGameStatus(
							gestorJugadores.getTurnoslist(),
							gestorBanco.getBanco(), gestorTablero.getTablero(),
							EstadoJuego.ESPERANDO_TURNO, null, jugadorActual,
							historyList, null);
					sendToOther(senderId, gameStatus);
				}
			} else {
				jugadorActual.resetCatidadDadosDobles();
				// if (jugadorActual.isHumano()) {
				// senderId = ((JugadorHumano) jugadorActual).getSenderID();
				// irALaCarcel(senderId);
				// } else {
				irALaCarcel(jugadorActual);
				// }
			}
		} else {
			jugadorActual.resetCatidadDadosDobles();
			jugadorSiguiente = gestorJugadores.siguienteTurno();

			history = new History(StringUtils.getFechaActual(),
					jugadorActual.getNombre(), "Finalizó su turno.");
			historyList.add(history);

			// ~~> Historia del siguiente turno
			history = new History(StringUtils.getFechaActual(),
					jugadorSiguiente.getNombre(), String.format(
							"Turno del jugador %s.",
							jugadorSiguiente.getNombre()));
			historyList.add(history);

			turnosList = gestorJugadores.getTurnoslist();

			// ~~~> Pregunto si es virtual. Si lo es envío un sendToAll
			// a todos los jugadores humanos. Si no, un mensaje al jugador
			// que le tocó el turno y otro mensaje al resto.
			if (jugadorSiguiente.isVirtual()) {
				status = new MonopolyGameStatus(turnosList,
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ESPERANDO_TURNO, null,
						gestorJugadores.getCurrentPlayer(), historyList, null);
				sendToAll(status);

				avanzarDeCasilleroJV();
			} else {
				estadoJuego = EstadoJuego.TIRAR_DADO;
				// ~~~> Pregunto si está preso. Si lo está le cambio el estado
				// a preso.
				if (gestorJugadores.getCurrentPlayer().estaPreso())
					estadoJuego = EstadoJuego.PRESO;

				status = new MonopolyGameStatus(turnosList,
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						estadoJuego, null, gestorJugadores.getCurrentPlayer(),
						historyList, null);
				sendToOne(((JugadorHumano) jugadorSiguiente).getSenderID(),
						status);

				status = new MonopolyGameStatus(turnosList,
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ESPERANDO_TURNO, null,
						gestorJugadores.getCurrentPlayer(), historyList, null);
				sendToOther(((JugadorHumano) jugadorSiguiente).getSenderID(),
						status);
			}
		}
	}

	/**
	 * Recibe una oferta de un jugador para comprar una propiedad de otro
	 * jugador. Si el Jugador que recibe la oferta es un JugadorVirtual,
	 * verifica si este la acepta y transfiere la propiedad si corresponde. Si
	 * es un JugadorHumano, le envía un mensaje con la oferta.
	 * 
	 * @param senderId
	 *            El jugador que hizo la oferta
	 * @param propiedad
	 *            La propiedad que quiere comprar
	 * @param oferta
	 *            El monto ofrecido por la propiedad
	 * @throws Exception
	 */
	public void ofrecerPorPropiedad(JugadorHumano comprador,
			TarjetaPropiedad propiedad, int oferta) throws Exception {

		Jugador dueno = propiedad.getJugador();
		boolean compra;

		if (dueno.isVirtual()) {
			JugadorVirtual jugadorVirtual = (JugadorVirtual) dueno;
			compra = gestorJugadoresVirtuales.decidirVenderPropiedad(propiedad,
					oferta, comprador, jugadorVirtual);

			this.transferirPropiedad(propiedad, comprador, oferta, compra);

		} else {
			BidForPropertyMessage bidMessage = new BidForPropertyMessage(
					comprador, juego.getUniqueID(), propiedad, oferta);
			sendToOne(((JugadorHumano) dueno).getSenderID(), bidMessage);
		}

	}

	/**
	 * Determina si un JugadorHumano acepta una oferta por una propiedad. En
	 * caso de que haya aceptado, realiza la transferencia de la propiedad.
	 * 
	 * @param comprador
	 *            El jugador que compra la propiedad
	 * @param propiedad
	 *            La propiedad que se transfiere
	 * @param oferta
	 *            El monto de la oferta
	 * @param resultado
	 *            {@code true} si el jugador aceptó la oferta
	 * @return {@code true} si el jugador aceptó la oferta
	 * @throws Exception
	 */
	public boolean terminarOfertaPorPropiedad(JugadorHumano comprador,
			TarjetaPropiedad propiedad, int oferta, boolean resultado)
			throws Exception {

		this.transferirPropiedad(propiedad, comprador, oferta, resultado);

		return resultado;
	}

	/**
	 * Realiza la transferencia de una propiedad de un jugador a otro
	 * 
	 * @param propiedad
	 *            La propiedad que se va a transferir
	 * @param comprador
	 *            El jugador que compra la propiedad
	 * @param monto
	 *            El monto por el cual se compra la propiedad
	 * @param compra
	 *            {@code true} si el jugador aceptó la oferta
	 * @throws Exception
	 */
	private void transferirPropiedad(TarjetaPropiedad propiedad,
			JugadorHumano comprador, int monto, boolean compra)
			throws Exception {

		String mensaje;
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();
		Jugador oldDueno = propiedad.getJugador();
		JugadorHumano newDueno = comprador;

		if (compra) {

			for (Jugador jugador : this.gestorJugadores.getTurnoslist()) {
				if (jugador.getNombre().equals(comprador.getNombre()))
					newDueno = (JugadorHumano) jugador;
				else if (jugador.getNombre().equals(
						propiedad.getJugador().getNombre()))
					oldDueno = jugador;
			}

			// Transferimos la propiedad y el dinero....
			gestorTablero.transferirPropiedad(
					gestorTablero.getTarjetaPropiedad(propiedad), newDueno,
					oldDueno, monto);

			// Enviar mensajes informado
			mensaje = String.format(
					"Le compró la propiedad %s al jugador %s por %s",
					propiedad.getNombre(), oldDueno.getNombre(),
					StringUtils.formatearAMoneda(monto));

		} else {
			mensaje = String.format(
					"No le compró la propiedad %s al jugador %s "
							+ "porque no aceptó la oferta de %s",
					propiedad.getNombre(), oldDueno.getNombre(),
					StringUtils.formatearAMoneda(monto));
		}

		historyList.add(new History(StringUtils.getFechaActual(), comprador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		sendToAll(status);

		// le informamos al comprador el resultado de la operacion...
		BidResultMessage bidMsg = new BidResultMessage(comprador,
				juego.getUniqueID(), propiedad, monto, compra);

		sendToOne(comprador.getSenderID(), bidMsg);
	}

	/**
	 * Método solicitado por un jugador humano para comprar una propiedad.
	 * 
	 * @param senderId
	 *            id de conexión de un jugador humano.
	 * @param nombrePropiedad
	 *            que se comprará.
	 * 
	 * @throws SinDineroException
	 * @throws Exception
	 */
	public void comprarPropiedad(int senderId, String nombrePropiedad)
			throws SinDineroException, Exception {
		TarjetaPropiedad tarjeta = gestorBanco.getBanco().getTarjetaPropiedad(
				nombrePropiedad);
		Jugador jugador = gestorJugadores.getJugadorHumano(senderId);
		comprarPropiedad(jugador, tarjeta);
	}

	/**
	 * Método para comprar una propiedad.
	 * 
	 * @param jugador
	 *            que realizará la compra.
	 * @param tarjeta
	 *            propiedad que se comprará.
	 * 
	 * @throws SinDineroException
	 * @throws Exception
	 */
	private void comprarPropiedad(Jugador jugador, TarjetaPropiedad tarjeta)
			throws SinDineroException, Exception {
		History history;
		int senderId = 0;
		gestorTablero.comprarPropiedad(jugador, tarjeta);

		if (jugador.isHumano()) {
			senderId = ((JugadorHumano) jugador).getSenderID();
			status = new MonopolyGameStatus(gestorJugadores.getTurnoslist(),
					gestorBanco.getBanco(), gestorTablero.getTablero(),
					EstadoJuego.JUGANDO, null,
					gestorJugadores.getCurrentPlayer(),
					new ArrayList<History>(), null);
			sendToOne(
					senderId,
					new CompleteTurnMessage(String.format(
							"Adquirió la propiedad %s.", tarjeta.getNombre()),
							EnumAction.BUY_PROPERTY, status));
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), String.format(
							"Adquirió la propiedad %s.", tarjeta.getNombre()));
			sendToOther(senderId, new HistoryGameMessage(history));
		} else {
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), String.format(
							"Adquirió la propiedad %s.", tarjeta.getNombre()));
			sendToAll(new HistoryGameMessage(history));
		}

		if (jugador.isHumano())
			siguienteTurno(true);
	}

	/**
	 * Método para adquirir una propiedad a un determinado monto.
	 * 
	 * @param jugadorComprador
	 *            , jugador que va a adquirir la propiedad.
	 * @param jugadorVendedor
	 *            , jugadpr que vende la propiedad. <code>null</code> si el
	 *            vendedor es el banco.
	 * @param tarjeta
	 *            , propiedad a adquirir.
	 * @param monto
	 *            , monto de la propiedad.
	 */
	private void adquirirPropiedad(Jugador jugadorComprador,
			Jugador jugadorVendedor, TarjetaPropiedad tarjeta, int monto)
			throws SinDineroException {
		if (jugadorVendedor == null) {
			tarjeta = gestorBanco.getBanco().getTarjetaPropiedad(tarjeta);
			gestorBanco.adquirirPropiedad(jugadorComprador, tarjeta, monto);
		} else {
			tarjeta = jugadorVendedor.getPropiedad(tarjeta);
			jugadorVendedor.venderPropiedad(tarjeta, jugadorComprador, monto);
		}
	}

	/**
	 * Hipoteca una propiedad y le paga el monto de la hipteca a su dueño.
	 * 
	 * @param propiedad
	 *            La propiedad que se va a hipotecar
	 * @return La {@code propiedad} hipotecada si se hipotecó. {@code null} si
	 *         no se pudo hipotecar. Ver
	 *         {@link TableroController#hipotecarPropiedad(TarjetaPropiedad, Jugador)}
	 *         .
	 * @throws Exception
	 *             Si no se puede enviar el mensaje al cliente.
	 */
	public TarjetaPropiedad hipotecarPropiedad(TarjetaPropiedad propiedad)
			throws Exception {

		Jugador jugador = gestorJugadores.getCurrentPlayer();
		TarjetaPropiedad propiedadToReturn = gestorTablero.hipotecarPropiedad(
				propiedad, jugador);

		String mensaje;
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();

		if (propiedadToReturn != null)
			mensaje = String.format("Hipotecó la propiedad %s y cobró %s.",
					propiedad.getNombre(), StringUtils
							.formatearAMoneda(propiedad.getValorHipotecario()));
		else
			mensaje = String.format("No pudo hipotecar la propiedad %s",
					propiedad.getNombre());

		historyList.add(new History(StringUtils.getFechaActual(), jugador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		sendToAll(status);

		return propiedadToReturn;

	}

	/**
	 * Deshipoteca una propiedad y le resta el monto de la deshipteca a su
	 * dueño.
	 * 
	 * @param propiedad
	 *            La propiedad que se va a deshipotecar
	 * @return La {@code propiedad} deshipotecada si se deshipotecó.
	 *         {@code null} si no se pudo deshipotecar. Ver
	 *         {@link TableroController#hipotecarPropiedad(TarjetaPropiedad, Jugador)}
	 *         .
	 * @throws Exception
	 *             Si no se puede enviar el mensaje al cliente.
	 */
	public TarjetaPropiedad deshipotecarPropiedad(TarjetaPropiedad propiedad)
			throws Exception {

		Jugador jugador = gestorJugadores.getCurrentPlayer();
		TarjetaPropiedad propiedadToReturn = gestorTablero
				.deshipotecarPropiedad(propiedad, jugador);

		String mensaje;
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();

		if (propiedadToReturn != null)
			mensaje = String.format("Deshipotecó la propiedad %s por %s.",
					propiedad.getNombre(), StringUtils
							.formatearAMoneda(propiedad
									.getValorDeshipotecario()));
		else
			mensaje = String.format("No pudo deshipotecar la propiedad %s",
					propiedad.getNombre());

		historyList.add(new History(StringUtils.getFechaActual(), jugador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		sendToAll(status);

		return propiedadToReturn;

	}

	/**
	 * Construye {@code cantidad} de edificios en el color de la {@code calle} y
	 * le cobra al dueño de la calle.
	 * 
	 * @param calle
	 *            La calle del color donde se va a construir
	 * @param cantidad
	 *            La cantidad de casas que se van a construir
	 * @return El dinero invertido en construir o {@code -1} si no se pudo
	 *         construir.
	 * @throws Exception
	 *             Si no se puede enviar el mensaje al cliente.
	 */
	public int construirEdificios(TarjetaCalle calle, int cantidad)
			throws Exception {

		Jugador jugador = gestorJugadores.getCurrentPlayer();
		int toReturn = gestorTablero.comprarEdificio(cantidad,
				(CasilleroCalle) calle.getCasillero());

		String mensaje;
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();

		if (toReturn != -1)
			mensaje = String.format(
					"Contruyó %s edificios sobre la calle %s por %s.",
					cantidad, calle.getNombre(),
					StringUtils.formatearAMoneda(toReturn));
		else
			mensaje = String.format("No pudo construir sobre la calle %s",
					calle.getNombre());

		historyList.add(new History(StringUtils.getFechaActual(), jugador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		sendToAll(status);

		return toReturn;

	}

	/**
	 * Vende {@code cantidad} de edificios en el color de la {@code calle} y le
	 * paga al dueño de la calle.
	 * 
	 * @param calle
	 *            La calle del color donde se quiere vender.
	 * @param cantidad
	 *            La cantidad de casas que se van a vender
	 * @return El dinero ganado por vender o {@code -1} si no se pudo vender.
	 * @throws Exception
	 *             Si no se puede enviar el mensaje al cliente.
	 */
	public int venderEdificios(TarjetaCalle calle, int cantidad)
			throws Exception {
		Jugador jugador = gestorJugadores.getCurrentPlayer();
		int cantVendida = gestorTablero.venderEdificio(cantidad,
				(CasilleroCalle) calle.getCasillero());

		int money = (calle.getPrecioVentaCadaCasa()) * cantVendida;

		String mensaje;
		EstadoJuego estadoJuegoJugadorActual = EstadoJuego.ACTUALIZANDO_ESTADO;
		List<Jugador> turnosList;
		List<History> historyList = new ArrayList<History>();

		if (cantVendida != -1)
			mensaje = String.format(
					"Vendió %s edificios de la calle %s por %s.", cantVendida,
					calle.getNombre(), StringUtils.formatearAMoneda(money));
		else
			mensaje = String.format("No pudo vender de la calle %s",
					calle.getNombre());

		historyList.add(new History(StringUtils.getFechaActual(), jugador
				.getNombre(), mensaje));

		turnosList = gestorJugadores.getTurnoslist();
		status = new MonopolyGameStatus(turnosList, gestorBanco.getBanco(),
				gestorTablero.getTablero(), estadoJuegoJugadorActual, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);
		sendToAll(status);

		return money;
	}

	/**
	 * Implementa el objetivo de la tarjeta Comunidad o Suerte Cuando un jugador
	 * humano saca una de las tarjetas.
	 * 
	 * @param senderId
	 *            id de jugador que envió el mensaje.
	 * @param tarjeta
	 *            Tarjeta obtenida en el casillero.
	 * 
	 */
	public void realizarObjetivoTarjeta(int senderId, Tarjeta tarjeta)
			throws Exception {
		Jugador jugador = gestorJugadores.getJugadorHumano(senderId);
		realizarObjetivoTarjeta(jugador, tarjeta);
	}

	/**
	 * Implementa el objetivo de la tarjeta Comunidad o Suerte Cuando un jugador
	 * saca una de las tarjetas.
	 * 
	 * @param jugador
	 * @param tarjeta
	 * @throws Exception
	 */
	private boolean realizarObjetivoTarjeta(Jugador jugador, Tarjeta tarjeta)
			throws SinDineroException, Exception {
		AccionEnTarjeta accion = null;
		try{
			if (tarjeta.isTarjetaComunidad()) {
				accion = gestorTablero.getGestorTarjetas().jugarTarjetaComunidad(
						jugador, (TarjetaComunidad) tarjeta);
			} else {
				accion = gestorTablero.getGestorTarjetas().jugarTarjetaSuerte(
						jugador, (TarjetaSuerte) tarjeta);
			}
			return jugarAccionTarjeta(jugador, accion);
		}
		catch(SinDineroException sde){
			if(tarjeta.isTarjetaComunidad())
				sde.setAccion(AccionEnCasillero.Accion.TARJETA_COMUNIDAD);
			else
				sde.setAccion(AccionEnCasillero.Accion.TARJETA_SUERTE);
			sde.setTarjeta(tarjeta);
			throw sde;
		}
	}

	/**
	 * Busca el objetivo de la tarjeta sacada del mazo a partir del Enum y
	 * ejecuta el objetivo.
	 * 
	 * <ol>
	 * <li>AccionEnTarjeta.PAGAR</li>
	 * <li>AccionEnTarjeta.COBRAR</li>
	 * <li>AccionEnTarjeta.COBRAR_TODOS</li>
	 * <li>AccionEnTarjeta.MOVER_A</li>
	 * <li>AccionEnTarjeta.IR_A_CARCEL</li>
	 * <li>AccionEnTarjeta.LIBRE_DE_CARCEL</li>
	 * </ol>
	 * </p>
	 * <p>
	 * Acciones tarjeta Suerte:
	 * <ol>
	 * <li>AccionEnTarjeta.MOVER_A</li>
	 * <li>AccionEnTarjeta.COBRAR</li>
	 * <li>AccionEnTarjeta.IR_A_CARCEL</li>
	 * <li>AccionEnTarjeta.MOVER</li>
	 * <li>AccionEnTarjeta.PAGAR_POR_CASA_HOTEL</li>
	 * <li>AccionEnTarjeta.LIBRE_DE_CARCEL</li>
	 * <li>AccionEnTarjeta.PAGAR</li>
	 * </ol>
	 * </p>
	 * 
	 * @param jugador
	 * @param tarjeta
	 * @throws Exception
	 */
	public boolean jugarAccionTarjeta(Jugador jugador,
			AccionEnTarjeta accionEnTarjeta) throws SinDineroException,
			Exception {
		String mensaje;
		Casillero casillero = null;
		AccionEnCasillero accionEnCasillero;
		MutableBoolean cobraSalida;

		int senderId = (jugador.isHumano() ? ((JugadorHumano) jugador)
				.getSenderID() : -1);

		switch (accionEnTarjeta) {

		// ~~~> El jugador cobra, el banco paga
		case COBRAR:
			gestorBanco.pagar(jugador, accionEnTarjeta.getMonto());
			break;

		// ~~~> El jugador paga, el banco cobra
		case PAGAR:
				gestorBanco.cobrar(jugador, accionEnTarjeta.getMonto());
			break;

		// ~~~> Cobra a todos los jugadores de la partida.
		case COBRAR_TODOS:
			this.contadorPagos = 0;
			String msgString = String.format("Debe pagar %s al jugador %s",
					StringUtils.formatearAMoneda(accionEnTarjeta.getMonto()),
					jugador.getNombre());
			PayToPlayerMessage msg = new PayToPlayerMessage(msgString, jugador,
					accionEnTarjeta.getMonto(), null);
			if (senderId == -1)
				sendToAll(msg);
			else
				sendToOther(senderId, msg);
			break;

		// ~~~> Debe pagar por cada casa u hotel
		case PAGAR_POR_CASA_HOTEL:
			try {
				gestorBanco.cobrarPorCasaYHotel(jugador,
						accionEnTarjeta.getPrecioPorCasa(),
						accionEnTarjeta.getPrecioPorHotel());
			} catch (SinDineroException e) {
				ExceptionMessage msgSinDinero = new ExceptionMessage(e);
				sendToOne(senderId, msgSinDinero);
				return false;
			}
			break;

		// ~~~> Se mueve a un determinado casillero.
		case MOVER:
			cobraSalida = new MutableBoolean(accionEnTarjeta.isCobraSalida());
			casillero = gestorTablero.moverAdelante(jugador,
					accionEnTarjeta.getNroCasilleros(), cobraSalida);
			if (cobraSalida.booleanValue())
				gestorBanco.pagarPasoSalida(jugador);
			break;

		// ~~~> Retrocede casilleros.
		case MOVER_A:
			cobraSalida = new MutableBoolean(accionEnTarjeta.isCobraSalida());
			casillero = gestorTablero.moverACasillero(jugador,
					accionEnTarjeta.getNroCasilleros(), cobraSalida);
			if (cobraSalida.booleanValue())
				gestorBanco.pagarPasoSalida(jugador);
			break;

		// ~~~> Tarjeta que deja libre de la cárcel.
		case LIBRE_DE_CARCEL:
			jugador.getTarjetaCarcelList().add(
					accionEnTarjeta.getTarjetaCarcel());
			gestorTablero.getGestorTarjetas().quitarTarjetaLibreDeCarcel(
					accionEnTarjeta.getTarjetaCarcel());

			break;

		// ~~~> Ir a la cárcel.
		case IR_A_CARCEL:
			gestorTablero.irACarcel(jugador);
			break;

		default:
			break;
		}

		mensaje = accionEnTarjeta.getMensaje();

		// ~~~> Se manda las historias a los demás participantes.
		sendToAll(new HistoryGameMessage(new History(
				StringUtils.getFechaActual(), jugador.getNombre(), mensaje)));

		switch (accionEnTarjeta) {
		case MOVER:
		case MOVER_A:
			accionEnCasillero = gestorTablero.getAccionEnCasillero(jugador,
					casillero);
			if (jugador.isHumano()) {
				jugarAccionCasillero(accionEnCasillero, jugador, casillero,
						senderId);
			} else {
				if (jugarAccionEnCasilleroJV(accionEnCasillero, (JugadorVirtual) jugador, casillero))
					siguienteTurno(true);
				// jugarAccionEnCasilleroJV(accionEnCasillero,
				// (JugadorVirtual) jugador, casillero);
			}
			break;
		case IR_A_CARCEL:
			if (jugador.isHumano())
				this.siguienteTurno(false);
			break;

		default:
			if (jugador.isHumano())
				this.siguienteTurno(true);
			break;
		}

		return true;
	}

	/**
	 * Método para llevar preso a un jugador.
	 * 
	 * @param senderId
	 *            id de conexión del jugador humano
	 */
	public void irALaCarcel(int senderId) throws Exception {
		Jugador jugador = gestorJugadores.getJugadorHumano(senderId);
		irALaCarcel(jugador);
	}

	/**
	 * Método para llevar preso a un jugador.
	 * 
	 * @param jugador
	 *            que irá preso.
	 * @throws Exception
	 */
	private void irALaCarcel(Jugador jugador) throws Exception {
		gestorTablero.irACarcel(jugador);

		sendToAll(new HistoryGameMessage(new History(
				StringUtils.getFechaActual(), jugador.getNombre(),
				"Fue a la cárcel")));

		// if (jugador.isHumano())
		siguienteTurno(false);
	}

	/**
	 * Método para cobrar el impuesto sobre el cápital.
	 * 
	 * @param senderId
	 *            id de conexión del jugador humano
	 * @param tipoImpuesto
	 */
	public void impuestoAlCapital(int senderId, int montoAPagar)
			throws Exception {
		// int monto = 0;
		History history;
		HistoryGameMessage msgHistory;
		Jugador jugador;

		jugador = gestorJugadores.getJugadorHumano(senderId);

		if (jugador.getDinero() >= montoAPagar) {
			gestorBanco.cobrar(jugador, montoAPagar);
			history = new History();
			history.setFecha(StringUtils.getFechaActual());
			history.setUsuario(jugador.getNombre());
			history.setMensaje(String.format("Pagó %s de impuesto al capital.",
					montoAPagar));
			msgHistory = new HistoryGameMessage(history);
			sendToAll(msgHistory);
		} else {
			SinDineroException sde = new SinDineroException(
					String.format(
							"No posees suficiente dinero para pagar el impuesto. Debes pagar %s.",
							StringUtils.formatearAMoneda(montoAPagar)),
					montoAPagar);
			sde.setAccion(AccionEnCasillero.Accion.IMPUESTO_SOBRE_CAPITAL);
			throw sde;
		}
		if (jugador.isHumano())
			siguienteTurno(true);
	}

	/**
	 * Método para que el jugador pague al banco un determinado {@code monto}.
	 * 
	 * @param senderId
	 *            id de conexión del jugador humano.
	 * @param monto
	 *            cantidad de dinero que el jugador le pagará al banco.
	 */
	public void pagarAlBanco(int senderId, int monto, String mensaje)
			throws Exception, SinDineroException {
		Jugador jugador;
		History history;
		HistoryGameMessage msgHistory;

		jugador = gestorJugadores.getJugadorHumano(senderId);
		gestorBanco.cobrar(jugador, monto);
		if (StringUtils.IsNullOrEmpty(mensaje)) {
			history = new History();
			history.setFecha(StringUtils.getFechaActual());
			history.setUsuario(jugador.getNombre());
			history.setMensaje(mensaje);
			msgHistory = new HistoryGameMessage(history);
			sendToAll(msgHistory);
		}
		if (jugador.isHumano())
			siguienteTurno(true);
	}

	/**
	 * Método ejecutado por un jugador humano para determinar si tiró dados
	 * dobles para salir de la cárcel.
	 * 
	 * @param senderId
	 *            Id de conexión de un jugador humano.
	 * @param dados
	 *            resultado de la tirada del jugador.
	 * @throws Exception
	 */
	public void tirarDadosDoblesSalirCarcel(int senderId, Dado dados)
			throws Exception {
		Jugador jugador;
		jugador = gestorJugadores.getJugadorHumano(senderId);
		tirarDadosDoblesSalirCarcel(jugador, dados);
	}

	/**
	 * Método para determinar si un jugador tiró dados dobles para salir de la
	 * cárcel.
	 * 
	 * @param jugador
	 *            que tiró los dados.
	 * @param dados
	 *            resultado obtenido de la tirada.
	 * @throws Exception
	 */
	private void tirarDadosDoblesSalirCarcel(Jugador jugador, Dado dados)
			throws Exception {
		MutableBoolean cobraSalida = new MutableBoolean(true);

		History history;
		String mensaje = "";
		Casillero casillero;
		AccionEnCasillero accion;
		HistoryGameMessage msgHistory;

		jugador.setUltimoResultado(dados);

		if (dados.EsDoble() || jugador.getCantidadTurnosCarcel() >= 2) {
			// ~~~> Sacó dobles, sale de la carcel.
			jugador.resetCantidadTurnosCarcel();
			jugador.setPreso(false);
			mensaje = dados.EsDoble() ? String.format(
					"Sacó dobles (%s - %s). Sale de la cárcel.",
					dados.getValorDado(1), dados.getValorDado(2))
					: "Tercer turno en la cárcel. Queda libre.";
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), mensaje);
			msgHistory = new HistoryGameMessage(history);
			sendToAll(msgHistory);

			// ~~> Sigue jugando
			casillero = gestorTablero.moverAdelante(jugador, dados.getSuma(),
					cobraSalida);
			// TODO Verificar si cuando cae en el casillero de salida cobra los
			// 200 o no.
			if (cobraSalida.booleanValue())
				gestorBanco.pagarPasoSalida(jugador);

			accion = gestorTablero.getAccionEnCasillero(jugador, casillero);

			if (jugador.isHumano())
				jugarAccionCasillero(accion, jugador, casillero,
						((JugadorHumano) jugador).getSenderID());
			else
				jugarAccionEnCasilleroJV(accion, (JugadorVirtual) jugador,
						casillero);
		} else {
			jugador.incrementarCantidadTurnosCarcel();
			mensaje = String.format(
					"No sacó dobles (%s - %s). Queda en la cárcel.",
					dados.getValorDado(1), dados.getValorDado(2));
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), mensaje);
			msgHistory = new HistoryGameMessage(history);
			sendToAll(msgHistory);
			siguienteTurno(false);
		}
	}

	/**
	 * Método para salir de la cárcel a través de un pago de dinero.
	 * 
	 * @param senderId
	 *            Identificador del jugador que esta abonando.
	 * @throws Exception
	 */
	public void pagarSalidaDeCarcel(int senderId, EnumSalidaCarcel tipoSalida)
			throws Exception, SinDineroException {
		Jugador jugador;
		Tarjeta tarjeta;
		History history;
		List<History> historyList;
		String mensaje = "";
		MonopolyGameStatus status;

		jugador = gestorJugadores.getJugadorHumano(senderId);
		historyList = new ArrayList<History>();
		if (tipoSalida == EnumSalidaCarcel.PAGAR) {
			if (jugador.getDinero() >= 50) {
				jugador.pagar(50);
				mensaje = String.format("Pagó %s para salir de la cárcel.",
						StringUtils.formatearAMoneda(50));
			} else {
				throw new SinDineroException(
						String.format(
								"No cuentas con suficiente dinero para pagar %s para quedar libre. Vende hoteles, casas o hipoteca propiedades para continuar con el juego.",
								StringUtils.formatearAMoneda(50)), 50);
			}
		} else {
			if (jugador.getTarjetaCarcelList().size() > 0) {
				tarjeta = jugador.getTarjetaCarcelList().get(0);
				jugador.getTarjetaCarcelList().remove(tarjeta);
				gestorTablero.getGestorTarjetas().agregarTarjetaLibreDeCarcel(
						tarjeta);
				mensaje = String.format(
						"Usó una tarjeta de la %s para salir de la cárcel.",
						tarjeta.isTarjetaSuerte() ? "Suerte"
								: "Caja de la Comunidad");
			}
		}

		jugador.setPreso(false);

		if (!StringUtils.IsNullOrEmpty(mensaje)) {
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), mensaje);
			historyList.add(history);
		}

		status = new MonopolyGameStatus(gestorJugadores.getTurnoslist(),
				gestorBanco.getBanco(), gestorTablero.getTablero(),
				EstadoJuego.ESPERANDO_TURNO, null,
				gestorJugadores.getCurrentPlayer(), historyList, null);

		sendToOther(senderId, status);

		status = new MonopolyGameStatus(gestorJugadores.getTurnoslist(),
				gestorBanco.getBanco(), gestorTablero.getTablero(),
				EstadoJuego.LIBRE, null, gestorJugadores.getCurrentPlayer(),
				historyList, null);

		sendToOne(senderId, status);

	}

	/**
	 * Método para pagar el alquiler por haber caído en el casillero del jugador
	 * propietario.
	 * 
	 * @param senderId
	 *            Id de conexión de un jugador humano.
	 * @param tarjetaPropiedad
	 * @throws Exception
	 */
	public void pagarAlquiler(int senderId, int propiedadId)
			throws SinDineroException, Exception {
		Jugador jugador;
		Jugador jugadorPropietario;
		Casillero casillero;
		TarjetaPropiedad tarjeta;
		int monto;

		jugador = gestorJugadores.getJugadorHumano(senderId);
		casillero = gestorTablero.getCasillero(propiedadId);
		tarjeta = gestorBanco.getBanco().getTarjetaPropiedadByCasillero(
				casillero);

		jugadorPropietario = tarjeta.getJugador();

		if (tarjeta.isPropiedadCalle()) {
			monto = ((TarjetaCalle) tarjeta).calcularAlquiler();
		} else if (tarjeta.isPropiedadCompania()) {
			monto = ((TarjetaCompania) tarjeta).calcularAlquiler(jugador
					.getUltimoResultado());
		} else {
			monto = ((TarjetaEstacion) tarjeta).calcularAlquiler();
		}

		jugador.pagarAJugador(jugadorPropietario, monto);

		sendToAll(new HistoryGameMessage(
				new History(
						StringUtils.getFechaActual(),
						jugador.getNombre(),
						String.format(
								"Pagó %s al jugador %s en concepto de alquiler de la propiedad.",
								StringUtils.formatearAMoneda(monto),
								jugadorPropietario.getNombre(),
								tarjeta.getNombre()))));

		siguienteTurno(true);

	}

	/**
	 * Método para llevar a cabo la subasta de una propiedad.
	 * 
	 * @param senderId
	 *            Id de conexión de un jugador humano.
	 * @param subastaStatus
	 *            Estado de la subasta.
	 * 
	 * @throws Exception
	 */
	public void subastar(int senderId, SubastaStatus subastaStatus)
			throws Exception {
		Jugador jugadorActual = gestorJugadores.getJugadorHumano(senderId);
		subastar(jugadorActual, subastaStatus);
	}

	private void subastar(Jugador jugadorActual, SubastaStatus subastaStatus)
			throws SinDineroException, Exception {

		Jugador jugadorTurno;

		// Si está creada, recién se genera, se agregan los jugadores.
		if (subastaStatus.estado == EnumEstadoSubasta.CREADA) {
			evaluarSubastaCreada(jugadorActual, subastaStatus);
		}
		/**
		 * Si la subasta ya se encuentra iniciada
		 */
		else {
			if (subastaStatus.estado == EnumEstadoSubasta.JUGANDO) {

				enviarNotificacionSubasta(
						"Subastó con "
								+ StringUtils
										.formatearAMoneda(subastaStatus.montoSubasta),
						jugadorActual);

				gestorSubasta.marcarComoPostor(jugadorActual);
				jugadorTurno = gestorSubasta
						.siguienteTurno(subastaStatus.montoSubasta);

				apostar(jugadorActual, jugadorTurno, subastaStatus);

			} else {
				GestorLogs.registrarError("No se debería dar el caso.");
			}
		}
	}

	/**
	 * Método para realizar la creación de la subasta y asignar los turnos.
	 * 
	 * @param jugadorCreador
	 * @param subastaStatus
	 * @param tarjeta
	 * @throws SinDineroException
	 * @throws Exception
	 */
	private void evaluarSubastaCreada(Jugador jugadorCreador,
			SubastaStatus subastaStatus) throws SinDineroException, Exception {

		AuctionFinishMessage msgFinalizarSubasta;
		AuctionDecideMessage msgDecidirSubasta;
		AuctionPropertyMessage msgActualizarSubasta;
		HistoryGameMessage msgHistoryGame;

		String mensaje;
		History history;

		Jugador jugadorTurno;
		TarjetaPropiedad tarjeta;

		int senderId = 0;
		boolean esVirtual = jugadorCreador.isVirtual();
		boolean decidirAceptarSubasta = false;

		int montoSubasta = 0;

		montoSubasta = subastaStatus.montoSubasta;
		tarjeta = gestorBanco.getBanco().getTarjetaPropiedad(
				subastaStatus.propiedadSubastada.getNombrePropiedad());

		// Validar que todos los jugadores tengan suficiente dinero.
		gestorSubasta = new SubastaController(jugadorCreador,
				subastaStatus.propiedadSubastada);
		for (Jugador jugador : gestorJugadores.getTurnoslist()) {
			if (subastaStatus.jugadorActual == null
					&& jugador.equals(jugadorCreador))
				continue;

			if (jugador.getDinero() > subastaStatus.montoSubasta)
				gestorSubasta.agregarJugadorASubasta(jugador);
		}

		switch (gestorSubasta.cantidadJugadores()) {
		case 0:
			/**
			 * Si no hay jugadores apostadores la propiedad queda disponible.
			 */
			mensaje = String
					.format("Ningún jugador posee dinero para subastar. La Propiedad %s queda disponible",
							tarjeta.getNombre());
			history = new History(StringUtils.getFechaActual(),
					jugadorCreador.getNombre(), mensaje);
			msgHistoryGame = new HistoryGameMessage(history);
			sendToAll(msgHistoryGame);

			Thread.sleep(tiempoDeEspera);

			// Si es virtual el jugador que inició la subasta
			// continúa el siguiente turno. Si no informo al jugador
			// humando para que finalice su turno.
			if (esVirtual) {
				siguienteTurno(true);
			} else {
				senderId = ((JugadorHumano) jugadorCreador).getSenderID();
				msgFinalizarSubasta = new AuctionFinishMessage(null, mensaje);
				sendToOne(senderId, msgFinalizarSubasta);
			}
			break;

		case 1:
			/**
			 * Notificar quien ganó la subasta.
			 */
			jugadorTurno = gestorSubasta.getJugadoresList().get(0);

			/**
			 * Si el jugador adjudicado es distinto al que inició la subasta.
			 */
			if (!jugadorCreador.equals(jugadorTurno)) {

				/**
				 * Si es virtual, debo preguntar si el jugador acepta la
				 * subasta.
				 */
				if (jugadorTurno.isVirtual()) {
					decidirAceptarSubasta = gestorJugadoresVirtuales
							.decidirAceptarSubasta(tarjeta, montoSubasta,
									(JugadorVirtual) jugadorTurno);
				}
				/**
				 * Si es humano envío mensaje preguntando si acepta la subasta
				 * de la propiedad ganada.
				 */
				else {
					mensaje = String
							.format("Haz ganado la subasta de la propiedad %s. Deseas pagar %s para hacerte propietario.",
									tarjeta.getNombre(), montoSubasta);
					msgDecidirSubasta = new AuctionDecideMessage(mensaje,
							montoSubasta, tarjeta, jugadorCreador);
					sendToOne(((JugadorHumano) jugadorTurno).getSenderID(),
							msgDecidirSubasta);
					break;
				}
			} else {
				// Ganador, único apostador.
				decidirAceptarSubasta = true;
			}

			/**
			 * El banco traspasa la propiedad al ganador de la subasta.
			 */
			if (decidirAceptarSubasta) {

				adquirirPropiedad(jugadorTurno, null, tarjeta, montoSubasta);

				mensaje = String.format(
						"Ganó la subasta de la propiedad %s con %s.",
						tarjeta.getNombre(),
						StringUtils.formatearAMoneda(montoSubasta));
				history = new History(StringUtils.getFechaActual(),
						jugadorTurno.getNombre(), mensaje);

				// Notifico mediante un GameStatus que el jugador se
				// adjudicó la propiedad.
				sendToAll(new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ACTUALIZANDO_ESTADO, null,
						gestorJugadores.getCurrentPlayer(),
						new ArrayList<History>(Arrays.asList(history)), null));

				Thread.sleep(tiempoDeEspera);

				/**
				 * Si el jugador que inició la subasta ganó, entonces mando un
				 * determinado mensaje. en caso contrario ganó un jugador
				 * virtual e informa quién la ganó.
				 */
				if (jugadorCreador.equals(jugadorTurno))
					mensaje = String.format(
							"Ganaste la subasta de la propiedad %s",
							tarjeta.getNombre());
				else {
					mensaje = String
							.format("%s ganó la subasta de la propiedad %s. Finalizó tu turno.",
									jugadorTurno.getNombre(),
									tarjeta.getNombre());
				}

				msgFinalizarSubasta = new AuctionFinishMessage(null, mensaje);
				sendToOne(senderId, msgFinalizarSubasta);
				if (gestorSubasta.getJugadorCreador().isVirtual()) {
					siguienteTurno(true);
				}
			}
			/**
			 * Si no decide aceptar la subasta. Notifico.
			 */
			else {
				mensaje = String
						.format("Ningún jugador posee dinero para subastar. La Propiedad %s queda disponible",
								tarjeta.getNombre());
				history = new History(StringUtils.getFechaActual(),
						jugadorTurno.getNombre(), mensaje);
				sendToAll(new HistoryGameMessage(history));

				Thread.sleep(tiempoDeEspera);

				if (jugadorCreador.isVirtual()) {
					siguienteTurno(true);
				} else {
					senderId = ((JugadorHumano) jugadorCreador).getSenderID();
					msgFinalizarSubasta = new AuctionFinishMessage(null,
							mensaje);
					sendToOne(senderId, msgFinalizarSubasta);
				}
			}
			break;

		default:
			/**
			 * Existen varios jugadores para apostar por la propiedad.
			 */
			gestorSubasta.inicializarVariables();
			jugadorTurno = gestorSubasta.siguienteTurno(montoSubasta);

			subastaStatus = new SubastaStatus(EnumEstadoSubasta.INICIADA,
					new ArrayList<History>(), jugadorCreador, tarjeta,
					montoSubasta);

			msgActualizarSubasta = new AuctionPropertyMessage(
					juego.getUniqueID(), subastaStatus);

			// Si el jugador actual es humano. Mando un mensaje al resto
			// para que inicien sus respectivas pantallas.
			if (jugadorCreador.isHumano())
				sendToOther(((JugadorHumano) jugadorCreador).getSenderID(),
						msgActualizarSubasta);
			else
				// Si es Virtual envío a todos el mensaje.
				sendToAll(msgActualizarSubasta);

			Thread.sleep(tiempoDeEspera);

			enviarNotificacionSubasta(
					"Inició la subasta con "
							+ StringUtils.formatearAMoneda(montoSubasta),
					jugadorCreador);

			subastaStatus = new SubastaStatus(EnumEstadoSubasta.JUGANDO,
					new ArrayList<History>(), jugadorTurno, tarjeta,
					montoSubasta);

			apostar(jugadorCreador, jugadorTurno, subastaStatus);
			break;
		}
	}

	private void apostar(Jugador jugadorActual, Jugador jugadorTurno,
			SubastaStatus subastaStatus) throws Exception {

		AuctionPropertyMessage msgActualizarSubasta;
		AuctionDecideMessage msgDecidirSubasta;
		AuctionFinishMessage msgFinalizarSubasta;
		TarjetaPropiedad tarjeta;
		History history;

		int senderId = 0;
		int montoSubasta = 0;
		int montoSubastaVirtual = 0;
		boolean decidirAceptarSubasta = false;
		String mensaje;

		enviarNotificacionSubasta("Turno para subastar.", jugadorTurno);

		montoSubasta = subastaStatus.montoSubasta;
		tarjeta = gestorBanco.getBanco().getTarjetaPropiedad(
				subastaStatus.propiedadSubastada.getNombrePropiedad());

		if (jugadorTurno.isVirtual()) {
			// El jugador virtual hace su apuesta.
			montoSubastaVirtual = gestorJugadoresVirtuales.pujar(tarjeta,
					montoSubasta, jugadorActual, (JugadorVirtual) jugadorTurno);

			Thread.sleep(tiempoDeEspera);

			if (montoSubastaVirtual > 0) {
				// Notifico a los jugadores que el jugador virtual
				// levantó el monto de la subasta.
				enviarNotificacionSubasta(
						"Subastó con "
								+ StringUtils
										.formatearAMoneda(montoSubastaVirtual),
						jugadorTurno);
				gestorSubasta.marcarComoPostor(jugadorTurno);
				montoSubasta = montoSubastaVirtual;
				jugadorActual = jugadorTurno;
				jugadorTurno = gestorSubasta.siguienteTurno(montoSubasta);

				subastaStatus = new SubastaStatus(EnumEstadoSubasta.JUGANDO,
						null, jugadorTurno, tarjeta, montoSubasta);

				apostar(jugadorActual, jugadorTurno, subastaStatus);

				return;
			}

			// ~~> Si llega hasta aquí es porque no decidió pujar

			// Notifico a los jugadores que el jugador virtual
			// abandonó la subasta.
			enviarNotificacionSubasta("Abandonó la Subasta.", jugadorTurno);
			jugadorActual = jugadorTurno;
			gestorSubasta.quitarJugadorDeSubasta(jugadorTurno);
			jugadorTurno = gestorSubasta.getJugadorActual();

			if (gestorSubasta.cantidadJugadores() > 1) {
				jugadorTurno = gestorSubasta.getJugadorActual();

				subastaStatus = new SubastaStatus(EnumEstadoSubasta.JUGANDO,
						null, jugadorTurno, tarjeta, montoSubasta);

				apostar(jugadorActual, jugadorTurno, subastaStatus);
				return;
			}

			/**
			 * Si llegó hasta aquí es porque queda el jugador ganador.
			 */
			if (gestorSubasta.EsLaPrimeraVez(jugadorTurno)) {
				// Debe preguntar si decide comprar.
				if (jugadorTurno.isVirtual()) {
					decidirAceptarSubasta = gestorJugadoresVirtuales
							.decidirAceptarSubasta(tarjeta, montoSubasta,
									(JugadorVirtual) jugadorTurno);
					/**
					 * El banco traspasa la propiedad al ganador de la subasta.
					 */
					if (decidirAceptarSubasta) {

						adquirirPropiedad(jugadorTurno, null, tarjeta,
								montoSubasta);

						mensaje = String.format(
								"Ganó la subasta de la propiedad %s con %s.",
								tarjeta.getNombre(),
								StringUtils.formatearAMoneda(montoSubasta));
						history = new History(StringUtils.getFechaActual(),
								jugadorTurno.getNombre(), mensaje);

						// Notifico mediante un GameStatus que el jugador se
						// adjudicó la propiedad.
						sendToAll(new MonopolyGameStatus(
								gestorJugadores.getTurnoslist(),
								gestorBanco.getBanco(),
								gestorTablero.getTablero(),
								EstadoJuego.ACTUALIZANDO_ESTADO, null,
								gestorJugadores.getCurrentPlayer(),
								new ArrayList<History>(Arrays.asList(history)),
								null));

						Thread.sleep(tiempoDeEspera);

						subastaStatus = new SubastaStatus(
								EnumEstadoSubasta.FINALIZADA, null,
								gestorSubasta.getJugadorCreador(), tarjeta,
								gestorSubasta.getUltimaPuja());
						subastaStatus
								.setMensaje(String
										.format("%s ganó la subasta de la propiedad %s. Finalizó tu turno.",
												jugadorTurno.getNombre(),
												tarjeta.getNombre()));

						sendToAll(new AuctionPropertyMessage("", subastaStatus));
					}
					/**
					 * Si no decide aceptar la subasta. Notifico.
					 */
					else {
						mensaje = String
								.format("Ningún jugador posee dinero para subastar. La Propiedad %s queda disponible",
										tarjeta.getNombre());
						history = new History(StringUtils.getFechaActual(),
								jugadorTurno.getNombre(), mensaje);
						sendToAll(new HistoryGameMessage(history));

						Thread.sleep(tiempoDeEspera);

						if (jugadorTurno.isVirtual()) {
							siguienteTurno(true);
						} else {
							senderId = ((JugadorHumano) jugadorTurno)
									.getSenderID();
							msgFinalizarSubasta = new AuctionFinishMessage(
									null, mensaje);
							sendToOne(senderId, msgFinalizarSubasta);
						}
					}
					if (gestorSubasta.getJugadorCreador().isVirtual()) {
						siguienteTurno(true);
					}
				} else {
					mensaje = String
							.format("Haz ganado la subasta de la propiedad %s. Deseas pagar %s para hacerte propietario.",
									tarjeta.getNombre(),
									gestorSubasta.getUltimaPuja());
					msgDecidirSubasta = new AuctionDecideMessage(mensaje,
							montoSubasta, tarjeta,
							gestorSubasta.getJugadorCreador());
					sendToOne(((JugadorHumano) jugadorTurno).getSenderID(),
							msgDecidirSubasta);
					return;
				}
			} else {

				adquirirPropiedad(jugadorTurno, null, tarjeta, montoSubasta);

				mensaje = String.format(
						"Ganó la subasta de la propiedad %s con %s.",
						tarjeta.getNombre(),
						StringUtils.formatearAMoneda(montoSubasta));
				history = new History(StringUtils.getFechaActual(),
						jugadorTurno.getNombre(), mensaje);

				sendToAll(new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ACTUALIZANDO_ESTADO, null,
						gestorJugadores.getCurrentPlayer(),
						new ArrayList<History>(Arrays.asList(history)), null));

				mensaje = String.format(
						"%s ganó la subasta de la propiedad %s con %s.",
						jugadorTurno.getNombre(), tarjeta.getNombre(),
						StringUtils.formatearAMoneda(montoSubasta));

				if (jugadorTurno.isVirtual()) {
					subastaStatus = new SubastaStatus(
							EnumEstadoSubasta.FINALIZADA,
							new ArrayList<History>(),
							gestorSubasta.getJugadorCreador(), tarjeta,
							montoSubasta);
					subastaStatus.setMensaje(mensaje);
					msgActualizarSubasta = new AuctionPropertyMessage("",
							subastaStatus);
					sendToAll(msgActualizarSubasta);

					if (gestorSubasta.getJugadorCreador().isVirtual()) {
						siguienteTurno(true);
					}
					
					return;
				}

					senderId = ((JugadorHumano) jugadorTurno).getSenderID();

					subastaStatus = new SubastaStatus(
							EnumEstadoSubasta.FINALIZADA,
							new ArrayList<History>(), gestorSubasta.getJugadorCreador(), tarjeta,
							montoSubasta);
					subastaStatus.setMensaje(mensaje);
					msgActualizarSubasta = new AuctionPropertyMessage("",
							subastaStatus);
					sendToAll(msgActualizarSubasta);
				
				if (gestorSubasta.getJugadorCreador().isVirtual()) {
					siguienteTurno(true);
				}
			}
		}
		// Si es humano.
		else {
			subastaStatus = new SubastaStatus(EnumEstadoSubasta.JUGANDO,
					new ArrayList<History>(), jugadorTurno, tarjeta,
					montoSubasta);
			msgActualizarSubasta = new AuctionPropertyMessage(
					juego.getUniqueID(), subastaStatus);
			sendToAll(msgActualizarSubasta);
		}
	}

	private void enviarNotificacionSubasta(String mensaje, Jugador jugador)
			throws Exception {
		AuctionNotifyMessage msgHistorySubasta;
		History history;

		history = new History(StringUtils.getFechaActual(),
				jugador.getNombre(), mensaje);

		msgHistorySubasta = new AuctionNotifyMessage(Arrays.asList(history));
		sendToAll(msgHistorySubasta);
		Thread.sleep(1000);
	}

	public void resultadoDecisionSubasta(Jugador jugador, int monto,
			TarjetaPropiedad propiedad, boolean respuesta,
			Jugador jugadorSubastador) throws Exception {

		AuctionFinishMessage msgFinalizarSubasta;
		int senderId = 0;
		String mensaje = "";
		History history;

		if (respuesta) {
			adquirirPropiedad(jugador, null, propiedad, monto);

			mensaje = String.format(
					"Ganó la subasta de la propiedad %s con %s.",
					propiedad.getNombre(), StringUtils.formatearAMoneda(monto));
			history = new History(StringUtils.getFechaActual(),
					jugador.getNombre(), mensaje);

			sendToAll(new MonopolyGameStatus(gestorJugadores.getTurnoslist(),
					gestorBanco.getBanco(), gestorTablero.getTablero(),
					EstadoJuego.ACTUALIZANDO_ESTADO, null,
					gestorJugadores.getCurrentPlayer(), new ArrayList<History>(
							Arrays.asList(history)), null));

			Thread.sleep(tiempoDeEspera);

			if (jugadorSubastador.isHumano()) {
				senderId = ((JugadorHumano) jugadorSubastador).getSenderID();
				msgFinalizarSubasta = new AuctionFinishMessage(null, mensaje);
				sendToOne(senderId, msgFinalizarSubasta);
			} else {
				siguienteTurno(true);
			}
		} else {

			mensaje = String
					.format("Ningún jugador posee dinero para subastar. La Propiedad %s queda disponible",
							propiedad.getNombre());

			if (jugadorSubastador.isHumano()) {
				senderId = ((JugadorHumano) jugadorSubastador).getSenderID();
				msgFinalizarSubasta = new AuctionFinishMessage(null, mensaje);
				sendToOne(senderId, msgFinalizarSubasta);
			} else {
				siguienteTurno(true);
			}
		}
	}

	public void finalizarSubasta(int senderId, int monto,
			TarjetaPropiedad tarjeta) throws Exception {

		String mensaje = "";
		History history;
		AuctionNotifyMessage msgHistorySubasta;
		AuctionPropertyMessage msgActualizarSubasta;
		AuctionDecideMessage msgDecidirSubasta;
		AuctionFinishMessage msgFinalizarSubasta;
		SubastaStatus subastaStatus;
		TarjetaPropiedad tarjetaPropiedad;
		Jugador jugadorActual;
		Jugador jugadorTurno;
		boolean decidirAceptarSubasta = false;

		jugadorActual = gestorJugadores.getJugadorHumano(senderId);
		tarjetaPropiedad = gestorBanco.getBanco().getTarjetaPropiedad(
				tarjeta.getNombrePropiedad());

		// Notifico a los jugadores que el jugador abandonó la subasta.
		mensaje = "Abandonó la Subasta.";
		history = new History(StringUtils.getFechaActual(),
				jugadorActual.getNombre(), mensaje);
		msgHistorySubasta = new AuctionNotifyMessage(new ArrayList<History>(
				Arrays.asList(history)));
		sendToAll(msgHistorySubasta);

		Thread.sleep(1500);

		gestorSubasta.quitarJugadorDeSubasta(jugadorActual);

		jugadorTurno = gestorSubasta.getJugadorActual();

		/**
		 * Si es igual a 1 queda el jugador ganador.
		 */
		if (gestorSubasta.cantidadJugadores() <= 1) {
			if (gestorSubasta.EsLaPrimeraVez(jugadorTurno)) {
				// Debe preguntar si decide comprar.
				if (jugadorTurno.isVirtual()) {
					decidirAceptarSubasta = gestorJugadoresVirtuales
							.decidirAceptarSubasta(tarjeta,
									gestorSubasta.getUltimaPuja(),
									(JugadorVirtual) jugadorTurno);
					/**
					 * El banco traspasa la propiedad al ganador de la subasta.
					 */
					if (decidirAceptarSubasta) {

						adquirirPropiedad(jugadorTurno, null, tarjeta,
								gestorSubasta.getUltimaPuja());

						mensaje = String.format(
								"Ganó la subasta de la propiedad %s con %s.",
								tarjeta.getNombre(), StringUtils
										.formatearAMoneda(gestorSubasta
												.getUltimaPuja()));
						history = new History(StringUtils.getFechaActual(),
								jugadorTurno.getNombre(), mensaje);

						// Notifico mediante un GameStatus que el jugador se
						// adjudicó la propiedad.
						sendToAll(new MonopolyGameStatus(
								gestorJugadores.getTurnoslist(),
								gestorBanco.getBanco(),
								gestorTablero.getTablero(),
								EstadoJuego.ACTUALIZANDO_ESTADO, null,
								gestorJugadores.getCurrentPlayer(),
								new ArrayList<History>(Arrays.asList(history)),
								null));

						Thread.sleep(tiempoDeEspera);

						subastaStatus = new SubastaStatus(
								EnumEstadoSubasta.FINALIZADA, null,
								gestorSubasta.getJugadorCreador(), tarjeta,
								gestorSubasta.getUltimaPuja());
						subastaStatus
								.setMensaje(String
										.format("%s ganó la subasta de la propiedad %s.",
												jugadorTurno.getNombre(),
												tarjeta.getNombre()));

						sendToAll(new AuctionPropertyMessage("", subastaStatus));
						
						if(gestorSubasta.getJugadorCreador().isVirtual())
							siguienteTurno(true);
						return;
					}
					/**
					 * Si no decide aceptar la subasta. Notifico.
					 */
					else {
						mensaje = String
								.format("Ningún jugador posee dinero para subastar. La Propiedad %s queda disponible",
										tarjeta.getNombre());
						history = new History(StringUtils.getFechaActual(),
								jugadorTurno.getNombre(), mensaje);
						sendToAll(new HistoryGameMessage(history));

						Thread.sleep(tiempoDeEspera);

						if (gestorSubasta.getJugadorCreador().isVirtual()) {
							siguienteTurno(true);
						} else {
							senderId = ((JugadorHumano) gestorSubasta.getJugadorCreador())
									.getSenderID();
							msgFinalizarSubasta = new AuctionFinishMessage(
									null, mensaje);
							sendToOne(senderId, msgFinalizarSubasta);
						}
						return;
					}
				}
				// Si nunca aposto y es humano
				else {
					mensaje = String
							.format("Haz ganado la subasta de la propiedad %s. Deseas pagar %s para hacerte propietario.",
									tarjeta.getNombre(),
									gestorSubasta.getUltimaPuja());
					msgDecidirSubasta = new AuctionDecideMessage(mensaje,
							gestorSubasta.getUltimaPuja(), tarjeta,
							gestorSubasta.getJugadorCreador());
					sendToOne(((JugadorHumano) jugadorTurno).getSenderID(),
							msgDecidirSubasta);
					return;
				}
			}
			// Si ya apostó una vez se le cobra lo que apostó.
			else {

				adquirirPropiedad(jugadorTurno, null, tarjetaPropiedad, monto);

				mensaje = String.format(
						"Ganó la subasta de la propiedad %s con %s.",
						tarjetaPropiedad.getNombre(),
						StringUtils.formatearAMoneda(monto));
				history = new History(StringUtils.getFechaActual(),
						jugadorTurno.getNombre(), mensaje);

				sendToAll(new MonopolyGameStatus(
						gestorJugadores.getTurnoslist(),
						gestorBanco.getBanco(), gestorTablero.getTablero(),
						EstadoJuego.ACTUALIZANDO_ESTADO, null,
						gestorJugadores.getCurrentPlayer(),
						new ArrayList<History>(Arrays.asList(history)), null));

				subastaStatus = new SubastaStatus(EnumEstadoSubasta.FINALIZADA,
						new ArrayList<History>(), gestorSubasta.getJugadorCreador(),
						tarjetaPropiedad, monto);

				msgActualizarSubasta = new AuctionPropertyMessage("",
						subastaStatus);
				sendToAll(msgActualizarSubasta);
				
				if(gestorSubasta.getJugadorCreador().isVirtual())
					siguienteTurno(true);
			}

		} else {
			subastaStatus = new SubastaStatus(EnumEstadoSubasta.JUGANDO, null,
					jugadorTurno, tarjetaPropiedad,
					gestorSubasta.getUltimaPuja());
			apostar(jugadorActual, jugadorTurno, subastaStatus);
		}
	}

	/**
	 * Método para enviar un mensaje por el chat.
	 * 
	 * @param history
	 *            mensaje del chat.
	 * 
	 * @throws Exception
	 */
	public void sendChatMessage(History history) throws Exception {
		ChatGameMessage msgChatGameMessage = new ChatGameMessage(null, history);
		sendToAll(msgChatGameMessage);
	}

	/**
	 * Método para mostrar la historia del juego.
	 * 
	 * @param history
	 * @throws Exception
	 */
	public void sendHistoryGame(History history) throws Exception {
		sendToAll(new HistoryGameMessage(history));
	}

	// =====================================================================//
	// ================= Métodos para determinar el juego. =================//
	// =====================================================================//

	/**
	 * Actualiza los datos de un juego guardado
	 * 
	 * @param juego
	 *            El juego a actualizar
	 * @return El juego actualizado
	 */
	public static Juego updateJuego(Juego juego) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		juegoDao.update(juego);
		return juego;
	}

	/**
	 * Busca un juego en la base de datos
	 * 
	 * @param juego
	 *            El {@code UniqueID} del juego que se quiere buscar
	 * @return El juego o {@code null} si no se encontró.
	 */
	public static Juego findJuegoByUniqueId(String uniqueID) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		return juegoDao.findJuegoByUniqueId(uniqueID);
	}

	/**
	 * Guarda un juego en la base de datos
	 * 
	 * @param juego
	 *            El juego que se quiere guardar
	 * @return El juego guardado
	 */
	public static Juego saveJuego(Juego juego) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		juegoDao.save(juego);
		return juego;
	}

	/**
	 * Borra un juego de la base de datos
	 * 
	 * @param juego
	 *            El juego que se quiere borrar
	 * @return El juego que se borro
	 */
	public static Juego deleteJuego(Juego juego) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		juegoDao.delete(juego);
		return juego;
	}

	/**
	 * Busca en la base de datos todos los juegos guardados de un usuario.
	 * 
	 * @param usuario
	 *            El usuario creador de los juegos que se quiere buscar.
	 * @return Los Juegos creados por el {@code usuario}
	 */
	public static List<Juego> buscarJuegosGuardados(Usuario usuario) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		return juegoDao.getJuegoGuardados(usuario);
	}

	/**
	 * Busca un juego guardado en la base de datos
	 * 
	 * @param UniqueID
	 *            El nombre del juego guardado
	 * @return El juego
	 */
	public static Juego buscarJuegoGuardado(String UniqueID) {
		IJuegoDao juegoDao = (IJuegoDao) appContext.getBean("juegoDao");
		return juegoDao.findJuegoByUniqueId(UniqueID);
	}

	// =====================================================================//
	// ============= Métodos para envío de mensaje al cliente. =============//
	// =====================================================================//

	/**
	 * Método para enviar un mensaje a un jugador en particular.
	 * 
	 * @param recipientID
	 *            Id de conexión de un jugador humano.
	 * @param message
	 *            Objecto mensaje.
	 * 
	 * @throws Exception
	 */
	private void sendToOne(int recipientID, Object message) throws Exception {
		PartidasController.getInstance().getMonopolyGame()
				.sendToOne(recipientID, message);
	}

	/**
	 * 
	 * Método que envía un determinado mensaje a todos los jugadores humanos del
	 * juego.
	 * 
	 * @param message
	 *            Objeto mensaje que recibirán los jugadores humanos.
	 */
	private void sendToAll(Object message) throws Exception {
		for (int key : gestorJugadores.getIdConnectionClient()) {
			PartidasController.getInstance().getMonopolyGame()
					.sendToOne(key, message);
		}
		Thread.sleep(1000);
	}

	/**
	 * 
	 * Método que envía un determinado mensaje a todos los jugadores humanos del
	 * juego excepto al que desencadena el mensaje.
	 * 
	 * @param message
	 *            Objeto mensaje que recibirán los jugadores humanos.
	 * @param senderId
	 *            Jugador que envía mensaje al resto de los participantes.
	 */
	private void sendToOther(int senderId, Object message) throws Exception {
		for (int key : gestorJugadores.getIdConnectionClient()) {
			if (key != senderId)
				PartidasController.getInstance().getMonopolyGame()
						.sendToOne(key, message);
		}
		Thread.sleep(1000);
	}

	// =====================================================================//
	// ========================== Getter & Setter ==========================//
	// =====================================================================//

	public Juego getJuego() {
		return juego;
	}

	public void setJuego(Juego juego) {
		this.juego = juego;
	}

	public BancoController getGestorBanco() {
		return gestorBanco;
	}

	public TableroController getGestorTablero() {
		return gestorTablero;
	}

	public JugadorController getGestorJugadores() {
		return gestorJugadores;
	}

	public JugadorVirtualController getGestorJugadoresVirtuales() {
		return gestorJugadoresVirtuales;
	}

	public int getCantJugadores() {
		return cantJugadores;
	}

	public void setCantJugadores(int cantJugadores) {
		this.cantJugadores = cantJugadores;
	}

	public Estado getEstadoJuego() {
		return estadoJuego;
	}

	public void setEstadoJuego(Estado estadoJuego) {
		this.estadoJuego = estadoJuego;
	}

	public void addContadorPagos() {
		this.contadorPagos++;
	}

	public boolean checkPagaronTodos() {
		return contadorPagos == this.getCantJugadores();
	}

}
