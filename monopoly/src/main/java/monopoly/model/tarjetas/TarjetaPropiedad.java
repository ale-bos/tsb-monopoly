/**
 * 
 */
package monopoly.model.tarjetas;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import monopoly.model.Jugador;
import monopoly.model.tablero.Casillero;

import org.hibernate.annotations.Type;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 * 
 * 
 */
@Entity
@Table(name = "tarjeta_propiedad", catalog = "monopoly_db")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class TarjetaPropiedad extends Tarjeta implements Serializable {

	private static final long serialVersionUID = -2385980028602703757L;

	public static String TARJETA_02 = "tarjeta02";
	public static String TARJETA_04 = "tarjeta04";
	public static String TARJETA_07 = "tarjeta07";
	public static String TARJETA_09 = "tarjeta09";
	public static String TARJETA_10 = "tarjeta10";
	public static String TARJETA_12 = "tarjeta12";
	public static String TARJETA_14 = "tarjeta14";
	public static String TARJETA_15 = "tarjeta15";
	public static String TARJETA_17 = "tarjeta17";
	public static String TARJETA_19 = "tarjeta19";
	public static String TARJETA_20 = "tarjeta20";
	public static String TARJETA_22 = "tarjeta22";
	public static String TARJETA_24 = "tarjeta24";
	public static String TARJETA_25 = "tarjeta25";
	public static String TARJETA_27 = "tarjeta27";
	public static String TARJETA_28 = "tarjeta28";
	public static String TARJETA_30 = "tarjeta30";
	public static String TARJETA_32 = "tarjeta32";
	public static String TARJETA_33 = "tarjeta33";
	public static String TARJETA_35 = "tarjeta35";
	public static String TARJETA_38 = "tarjeta38";
	public static String TARJETA_40 = "tarjeta40";
	public static String TARJETA_06 = "tarjeta06";
	public static String TARJETA_16 = "tarjeta16";
	public static String TARJETA_26 = "tarjeta26";
	public static String TARJETA_36 = "tarjeta36";
	public static String TARJETA_29 = "tarjeta29";
	public static String TARJETA_13 = "tarjeta13";

	@Id
	@GeneratedValue
	@Column(name = "tarjetaPropiedadID")
	private int idTarjeta;

	@ManyToOne
	@JoinColumn(name = "jugadorID")
	private Jugador jugador;

	@Column(name = "nombre")
	private String nombre;

	@Column(name = "valorHipotecario")
	private int valorHipotecario;

	@Column(name = "pathImagenPropiedad")
	private String pathImagenPropiedad;

	@Column(name = "pathImagenFrente")
	private String pathImagenFrente;

	@Column(name = "pathImagenDorso")
	private String pathImagenDorso;

	@Column(name = "isHipotecada", columnDefinition = "TINYINT")
	@Type(type = "org.hibernate.type.NumericBooleanType")
	private boolean hipotecada;

	@Column(name = "valorPropiedad")
	private int valorPropiedad;

	@Column(name = "nombrePropiedad")
	private String nombrePropiedad;

	@Transient
	private Casillero casillero;

	public TarjetaPropiedad() {

	}

	/**
	 * @param jugador
	 * @param nombre
	 * @param valorHipotecario
	 * @param valorPropiedad
	 */
	public TarjetaPropiedad(Jugador jugador, String nombre,
			int valorHipotecario, String nombreImagen, int valorPropiedad,
			Casillero casillero) {
		super();
		this.jugador = jugador;
		this.nombre = nombre;
		this.valorHipotecario = valorHipotecario;
		this.pathImagenPropiedad = nombreImagen;
		this.hipotecada = false;
		this.valorPropiedad = valorPropiedad;
		this.casillero = casillero;
	}

	/**
	 * Informa si una propiedad está en condiciones de ser hipotecada. Verifica
	 * que la propiedad no esté hipotecada, y si es calle, controla que no tenga
	 * construcciones (casas u hoteles)
	 * 
	 * @return {@code true} si la propiedad se puede hipotecar.
	 */
	public boolean isHipotecable() {
		// Controlamos que la propiedad no esté hipotecada...
		if (this.isHipotecada())
			return false;

		// Si es calle, controlamos que no tenga consrucciones...
		if (this.isPropiedadCalle()) {
			TarjetaCalle calle = (TarjetaCalle) this;
			if (calle.getNroCasas() != 0)
				return false;
		}
		return true;
	}

	/**
	 * Informa si una propiedad está en condiciones de ser dehipotecada.
	 * Verifica que la propiedad esté hipotecada y que el dueño de la propiedad
	 * tenga dinreo suficiente para pagar el precio de la deshipoteca.
	 * 
	 * @return {@code true} si la propiedad se puede deshipotecar.
	 */
	public boolean isDeshipotecable() {
		if (!this.isHipotecada())
			return false;

		if (!this.getJugador().puedePagarConEfectivo(
				this.getValorDeshipotecario()))
			return false;

		return true;
	}

	public Jugador getJugador() {
		return jugador;
	}

	public void setJugador(Jugador jugador) {
		this.jugador = jugador;
	}

	public int getIdTarjeta() {
		return idTarjeta;
	}

	public void setIdTarjeta(int idTarjeta) {
		this.idTarjeta = idTarjeta;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public int getValorHipotecario() {
		return valorHipotecario;
	}

	public int getValorDeshipotecario() {
		return (int) ((double) valorHipotecario * 1.10);
	}

	public void setValorHipotecario(int valorHipotecario) {
		this.valorHipotecario = valorHipotecario;
	}

	public String getPathImagenPropiedad() {
		return pathImagenPropiedad;
	}

	public void setPathImagenPropiedad(String nombreImagen) {
		this.pathImagenPropiedad = nombreImagen;
	}

	public int getValorPropiedad() {
		return valorPropiedad;
	}

	public void setValorPropiedad(int valorPropiedad) {
		this.valorPropiedad = valorPropiedad;
	}

	public boolean isHipotecada() {
		return hipotecada;
	}

	public void setHipotecada(boolean hipotecada) {
		this.hipotecada = hipotecada;
	}

	public String getNombrePropiedad() {
		return nombrePropiedad;
	}

	public void setNombrePropiedad(String nombrePropiedad) {
		this.nombrePropiedad = nombrePropiedad;
	}

	public Casillero getCasillero() {
		return casillero;
	}

	public void setCasillero(Casillero casillero) {
		this.casillero = casillero;
	}

	public String getPathImagenFrente() {
		return pathImagenFrente;
	}

	public void setPathImagenFrente(String pathImagenFrente) {
		this.pathImagenFrente = pathImagenFrente;
	}

	public String getPathImagenDorso() {
		return pathImagenDorso;
	}

	public void setPathImagenDorso(String pathImagenDorso) {
		this.pathImagenDorso = pathImagenDorso;
	}

	/**
	 * Devuelve true o false si es una Calle
	 * 
	 * @return True si es tarjeta calle, False caso contrario.
	 */
	public boolean isPropiedadCalle() {
		return this instanceof TarjetaCalle;
	}

	/**
	 * Devuelve true o false si es una compania.
	 * 
	 * @return True si es tarjeta compania, False caso contrario.
	 */
	public boolean isPropiedadCompania() {
		return this instanceof TarjetaCompania;
	}

	/**
	 * Devuelve true o false si es una estación
	 * 
	 * @return True si es una tarjeta estación, False caso contrario.
	 */
	public boolean isPropiedadEstacion() {
		return this instanceof TarjetaEstacion;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "TarjetaPropiedad [ nombre=" + nombre + ", valorHipotecario="
				+ valorHipotecario + ",valoPropiedadr=" + valorPropiedad + " ]";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object object) {
		if (object == this)
			return true;

		if (object == null || getClass() != object.getClass())
			return false;

		TarjetaPropiedad tp = (TarjetaPropiedad) object;
		if (this.idTarjeta != tp.idTarjeta)
			return false;

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.getIdTarjeta();
	}

	// public boolean hipotecarPropiedad() {
	// return this.getJugador().getJuego().getBanco()
	// .hipotecarPropiedad(jugador, this);
	// }
	//
	// public boolean deshipotecarPropiedad() {
	// return this.getJugador().getJuego().getBanco()
	// .deshipotecarPropiedad(this.getJugador(), this);
	// }

}
