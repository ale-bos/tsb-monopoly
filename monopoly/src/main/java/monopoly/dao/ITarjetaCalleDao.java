/**
 * 
 */
package monopoly.dao;

import java.util.List;

import monopoly.model.tarjetas.TarjetaCalle;

/**
 * @author Bostico Alejandro
 * @author Moreno Pablo
 * 
 * 
 */
public interface ITarjetaCalleDao {

    void save(TarjetaCalle tarjeta);

    void update(TarjetaCalle tarjeta);

    void delete(TarjetaCalle tarjeta);
    
    List<TarjetaCalle> getAll();
    
    public TarjetaCalle findByNombre(String nombreCalle);

}
