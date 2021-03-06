/**
 * 
 */
package monopoly.controller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import monopoly.model.Banco;
import monopoly.model.Jugador;
import monopoly.model.JugadorVirtual;
import monopoly.model.tablero.Casillero;
import monopoly.model.tablero.CasilleroCalle;
import monopoly.model.tablero.CasilleroCompania;
import monopoly.model.tablero.CasilleroEstacion;
import monopoly.model.tarjetas.TarjetaCalle;
import monopoly.model.tarjetas.TarjetaPropiedad;
import monopoly.util.GestorLogs;
import monopoly.util.exception.PropiedadNoDeshipotecableException;
import monopoly.util.exception.PropiedadNoHipotecableException;
import monopoly.util.exception.SinDineroException;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 *
 */
public class BancoController implements Serializable {

	private static final long serialVersionUID = -8147338284085100737L;
	private Banco banco;

	public BancoController(Casillero[] casilleros) {
		List<TarjetaPropiedad> tarjetasList = new ArrayList<TarjetaPropiedad>();
		for (Casillero casillero : casilleros) {
			switch (casillero.getTipoCasillero()) {
			case C_CALLE:
				tarjetasList
						.add(((CasilleroCalle) casillero).getTarjetaCalle());
				break;
			case C_ESTACION:
				tarjetasList.add(((CasilleroEstacion) casillero)
						.getTarjetaEstacion());
				break;
			case C_COMPANIA:
				tarjetasList.add(((CasilleroCompania) casillero)
						.getTarjetaCompania());
				break;
			default:
				break;
			}
		}

		this.banco = new Banco(tarjetasList, 32, 12);
	}

	public Banco getBanco() {
		return banco;
	}

	public void setBanco(Banco banco) {
		this.banco = banco;
	}

	/**
	 * resta del dinero del jugador el monto indicado
	 * 
	 * @param jugador
	 *            el jugador al que se quiere cobrar
	 * @param monto
	 *            el monto que se quiere cobrar
	 * @throws SinDineroException
	 *             Si el jugador no tiene dinero para pagar
	 */
	public void cobrar(Jugador jugador, int monto) throws SinDineroException {
		JuegoController juegoController = PartidasController.getInstance()
				.buscarControladorJuego(jugador.getJuego().getUniqueID());
		if (jugador.isVirtual()) {
			if (juegoController.getGestorJugadoresVirtuales().pagar(
					(JugadorVirtual) jugador, monto) == -1) {
			}
		} else {
			if (!jugador.pagar(monto)) {
				throw new SinDineroException(
						String.format(
								"El jugador %s no posee dinero suficiente para pagar %s €",
								jugador.getNombre(), monto), monto);
			}
		}
	}

	/**
	 * suma al dinero del jugador el monto indicado
	 * 
	 * @param jugador
	 *            el jugador al que se debe pagar
	 * @param monto
	 *            el monto que se debe pagar
	 * @return true si se pudo pagar el monto
	 */
	public boolean pagar(Jugador jugador, int monto) {
		jugador.cobrar(monto);
		return true;
	}

	/**
	 * suma 200 euros al jugador por pasar por la salida.
	 * 
	 * @param jugador
	 *            jugador que va a cobrar.
	 */
	public void pagarPasoSalida(Jugador jugador) {
		jugador.cobrar(200);
	}

	/**
	 * toma una propiedad en hipoteca y paga al jugador el valor indicado para
	 * la hipoteca de esta propiedad
	 * 
	 * @param jugador
	 *            el jugador que hipoteca la propiedad
	 * @param tarjetaPropiedad
	 *            la propiedad que hipoteca el jugador
	 * @return {@code true} si se hipotecó.
	 * @deprecated Usar {@link #hipotecarPropiedad(TarjetaPropiedad)} que le
	 *             suma el dinero al dueño de la propiedad. No es necesario
	 *             enviarle el jugador. Además, este método no verifica si la
	 *             propiedad está en condiciones de ser hipotecada.
	 */
	@Deprecated
	public boolean hipotecarPropiedad(Jugador jugador,
			TarjetaPropiedad tarjetaPropiedad) {
		if (this.pagar(jugador, tarjetaPropiedad.getValorHipotecario())) {
			tarjetaPropiedad.setHipotecada(true);
			return true;
		} else
			return false;
	}

	/**
	 * Toma una propiedad en hipoteca y paga al jugador "dueño" de esa propiedad
	 * el valor hipotecario. Antes de hipotecar, verifica que la propiedad no
	 * esté ya hipotecada y que no tenga construcciones en el caso de sea una
	 * calle.
	 * 
	 * @param propiedad
	 *            La propiedad que se quiere hipotecar
	 * @return La propiedad hipotecada
	 * @throws PropiedadNoHipotecableException
	 *             Si la propiedad no se puede hipotecar por alguno de los
	 *             siguientes motivos:
	 *             <ul>
	 *             <li>La propiedad ya está hipotecada. No se puede volver a
	 *             hipotecar.</li>
	 *             <li>La propiedad tiene construcciones. No se puede hipotecar.
	 *             </li>
	 *             <li>La propiedad no tiene dueño. No se puede hipotecar.</li>
	 *             </ul>
	 */
	public TarjetaPropiedad hipotecarPropiedad(TarjetaPropiedad propiedad)
			throws PropiedadNoHipotecableException {
		// Controlamos que la propiedad no esté hipotecada...
		if (propiedad.isHipotecada())
			throw new PropiedadNoHipotecableException(
					String.format(
							"La propiedad %s ya está hipotecada. No se puede volver a hipotecar.",
							propiedad.getNombre()));

		// Si es calle, controlamos que no tenga construcciones...
		if (propiedad.isPropiedadCalle()) {
			TarjetaCalle calle = (TarjetaCalle) propiedad;
			if (calle.getNroCasas() != 0)
				throw new PropiedadNoHipotecableException(
						String.format(
								"La propiedad %s tiene construcciones. No se puede hipotecar.",
								propiedad.getNombre()));
		}

		// Controlamos que la propiedad tenga dueño...
		if (propiedad.getJugador() == null)
			throw new PropiedadNoHipotecableException(String.format(
					"La propiedad %s no tiene dueño. No se puede hipotecar.",
					propiedad.getNombre()));

		// Si se puede hipotecar, hipotecamos...
		Jugador jugador = propiedad.getJugador();
		if (this.pagar(jugador, propiedad.getValorHipotecario())) {
			propiedad.setHipotecada(true);
			GestorLogs.registrarLog(String.format(
					"El Jugador %s hipotecó %s por %s", jugador.getNombre(),
					propiedad.getNombre(), propiedad.getValorHipotecario()));
			return propiedad;
		} else
			return null;
	}

	/**
	 * Toma una propiedad hipotecada y cobra al jugador "dueño" de esa propiedad
	 * el costo de deshipotecarla. Antes de deshipotecar, verifica que la
	 * propiedad esté ya hipotecada.
	 * 
	 * @param propiedad
	 *            La propiedad que se quiere deshipotecar
	 * @return La propiedad deshipotecada
	 * @throws PropiedadNoDeshipotecableException
	 *             Si la propiedad no está hipotecada
	 * @throws SinDineroException
	 *             Si el dueño de la propiedad no tiene dinero en efectivo para
	 *             deshipotecar la propiedad
	 */
	public TarjetaPropiedad deshipotecarPropiedad(TarjetaPropiedad propiedad)
			throws PropiedadNoDeshipotecableException, SinDineroException {
		// Controlamos que la propiedad esté hipotecada...
		if (!propiedad.isHipotecada())
			throw new PropiedadNoDeshipotecableException(
					String.format(
							"La propiedad %s no está hipotecada. No se puede deshipotecar.",
							propiedad.getNombre()));

		Jugador jugador = propiedad.getJugador();

		if (jugador.pagar(propiedad.getValorDeshipotecario())) {
			propiedad.setHipotecada(false);
			GestorLogs.registrarLog(String.format(
					"El Jugador %s deshipotecó %s por %s", jugador.getNombre(),
					propiedad.getNombre(), propiedad.getValorDeshipotecario()));
			return propiedad;
		} else {
			throw new SinDineroException(
					String.format(
							"El jugador %s no tiene %s en efectivo para deshipotecar %s",
							jugador.getNombre(),
							propiedad.getValorDeshipotecario(),
							propiedad.getNombre()),propiedad.getValorDeshipotecario());
		}

	}

	/**
	 * deshipoteca una propiedad indicada y cobra al jugador la hipoteca de esta
	 * propiedad mas el 10 %
	 * 
	 * @param jugador
	 *            el jugador que deshipoteca la propiedad
	 * @param tarjetaPropiedad
	 *            la propiedad que deshipoteca el jugador
	 * @throws SinDineroException
	 *             Si no tiene dinero para deshipotecar
	 * @deprecated Usar {@link #deshipotecarPropiedad(TarjetaPropiedad)} que le
	 *             resta el dinero al dueño de la propiedad. No es necesario
	 *             enviarle el jugador. Además, este método no verifica si la
	 *             propiedad está en condiciones de ser deshipotecada.
	 */
	@Deprecated
	public void deshipotecarPropiedad(Jugador jugador,
			TarjetaPropiedad tarjetaPropiedad) throws SinDineroException {
		this.cobrar(jugador,
				(int) (tarjetaPropiedad.getValorHipotecario() * 1.10));
		tarjetaPropiedad.setHipotecada(false);
	}

	/**
	 * vende una propiedad al jugador
	 * 
	 * @param jugador
	 *            el jugador que compra la propiedad
	 * @param tarjetaPropiedad
	 *            la propiedad compra el jugador
	 * @throws SinDineroException
	 *             Si el comprador no tiene dinero para pagar la propiedad
	 */
	public void venderPropiedad(Jugador jugador,
			TarjetaPropiedad tarjetaPropiedad) throws SinDineroException {
		this.cobrar(jugador, tarjetaPropiedad.getValorPropiedad());
		jugador.adquirirPropiedad(tarjetaPropiedad);
	}

	/**
	 * Adquiere una propiedad a un monto determinado.
	 * 
	 * @param jugador
	 *            El jugador que compra la propiedad
	 * @param tarjetaPropiedad
	 *            La tarjeta de la propiedad que adquiere
	 * @param monto
	 *            El precio que paga por la propiedad
	 * @throws SinDineroException
	 *             Si no le alcanza en dinero para adquirir la propiedad
	 */
	public void adquirirPropiedad(Jugador jugador,
			TarjetaPropiedad tarjetaPropiedad, int monto)
			throws SinDineroException {
		this.cobrar(jugador, monto);
		jugador.adquirirPropiedad(tarjetaPropiedad);
	}

	/**
	 * Cobra al jugador los montos especificados por cada casa y cada hotel que
	 * tenga
	 * 
	 * @param jugador
	 *            El jugador al que se le tiene que cobrar
	 * @param cuantoPorCasa
	 *            El monto que tiene que pagar por cada casa que tenga
	 * @param cuantoPorHotel
	 *            El monto que tiene que pagar por cada hotel que tenga
	 * @return El monto total que pagó el jugador:
	 *         {@code ((cuantoPorCasa * cantCasas)
	 *         + (cuantoPorHotel * cantHoteles))}
	 * @throws SinDineroException
	 *             Si el jugador no tiene dinero suficiente para pagar
	 */
	public int cobrarPorCasaYHotel(Jugador jugador, int cuantoPorCasa,
			int cuantoPorHotel) throws SinDineroException {

		JuegoController juegoController = PartidasController.getInstance()
				.buscarControladorJuego(jugador.getJuego().getUniqueID());

		int montoCasas = jugador.getNroCasas() * cuantoPorCasa;
		int montoHoteles = jugador.getNroHoteles() * cuantoPorHotel;

		// si el monto a pagar es cero, salimos...
		if ((montoCasas + montoHoteles) == 0)
			return 0;

		if (jugador.isVirtual()) {
			juegoController.getGestorJugadoresVirtuales().pagar(
					(JugadorVirtual) jugador, montoCasas + montoHoteles);
		} else {
			this.cobrar(jugador, montoCasas + montoHoteles);
		}
		return montoCasas + montoHoteles;
	}

	/**
	 * Permite que {@code jugadorCobra} cobre {@code cantidad} de los demás
	 * jugadores
	 * 
	 * @param jugadorCobra
	 *            El jugador que cobra
	 * @param cantidad
	 *            La cantidad que cobra de cada jugador
	 */
	public void cobrarATodosPagarAUno(Jugador jugadorCobra, int cantidad) throws SinDineroException, Exception {

		JuegoController juegoController = PartidasController.getInstance()
				.buscarControladorJuego(jugadorCobra.getJuego().getUniqueID());
		List<Jugador> jugadoresList = juegoController.getGestorJugadores()
				.getTurnoslist();

		for (Jugador jugador : jugadoresList) {
			if (!jugador.equals(jugadorCobra)) {

				if (jugador instanceof JugadorVirtual)
					juegoController.getGestorJugadoresVirtuales()
							.pagarAJugador((JugadorVirtual) jugador,
									jugadorCobra, cantidad);
				else
					jugador.pagarAJugador(jugadorCobra, cantidad);
			}
		}

	}

}
