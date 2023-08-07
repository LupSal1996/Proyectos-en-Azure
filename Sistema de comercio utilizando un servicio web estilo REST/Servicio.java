package servicio_comercio;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.QueryParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.Response;

import java.sql.*;
import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import com.google.gson.*;

// la URL del servicio web es http://localhost:8080/Servicio/rest/ws
// donde:
//	"Servicio" es el dominio del servicio web (es decir, el nombre de archivo Servicio.war)
//	"rest" se define en la etiqueta <url-pattern> de <servlet-mapping> en el archivo WEB-INF\web.xml
//	"ws" se define en la siguiente anotación @Path de la clase Servicio
@Path("ws")
public class Servicio {
  static DataSource pool = null;
  static {
    try {
      Context ctx = new InitialContext();
      pool = (DataSource) ctx.lookup("java:comp/env/jdbc/datasource_Servicio");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  static Gson j = new GsonBuilder().registerTypeAdapter(byte[].class, new AdaptadorGsonBase64())
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").create();

  @POST
  @Path("guardar_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)

  public Response guardarArticulo(String json) throws Exception {

    ParamAltaArticulo p = (ParamAltaArticulo) j.fromJson(json, ParamAltaArticulo.class);
    Articulo articulo = p.articulo;
    Connection conexion = pool.getConnection();

    if (articulo.nombre == null || articulo.nombre.equals("")) {
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar el nombre del artículo"))).build();
    }

    if (articulo.descripcion == null || articulo.descripcion.equals("")) {
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar la descripción del artículo"))).build();
    }

    if (articulo.precio <= 0) {
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar un precio válido"))).build();
    }

    if (articulo.cantidad <= 0) {
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar una cantidad válida"))).build();
    }

    try {
      conexion.setAutoCommit(false);
      PreparedStatement stmt = conexion.prepareStatement(
          "INSERT INTO articulos(id_articulo, nombre, descripcion, precio, cantidad, fotografia) VALUES (0, ?, ?, ?, ?, ?)");

      try {
        stmt.setString(1, articulo.nombre);
        stmt.setString(2, articulo.descripcion);
        stmt.setInt(3, articulo.precio);
        stmt.setInt(4, articulo.cantidad);
        stmt.setBytes(5, articulo.fotografia);

        stmt.executeUpdate();
      } finally {
        stmt.close();
      }
      conexion.commit();
    } catch (Exception e) {
      conexion.rollback();
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.setAutoCommit(true);
      conexion.close();
    }
    return Response.ok().build();
  }

  @POST
  @Path("consulta_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response consultaArticulo(String json) throws Exception {
    ParamConsultaArticulo p = (ParamConsultaArticulo) j.fromJson(json, ParamConsultaArticulo.class);
    String palabraClave = p.palabraClave;
    Connection conexion = pool.getConnection();
    ArrayList<Articulo> articulos = new ArrayList<>();

    try {
      PreparedStatement stmt = conexion.prepareStatement(
          "SELECT fotografia, nombre, precio, descripcion, id_articulo FROM articulos WHERE nombre LIKE ? OR descripcion LIKE ?");
      stmt.setString(1, "%" + palabraClave + "%");
      stmt.setString(2, "%" + palabraClave + "%");
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        byte[] fotografia = rs.getBytes("fotografia");
        String nombre = rs.getString("nombre");
        int precio = rs.getInt("precio");
        String descripcion = rs.getString("descripcion");
        int id = rs.getInt("id_articulo");

        Articulo articulo = new Articulo();
        articulo.fotografia = fotografia;
        articulo.nombre = nombre;
        articulo.precio = precio;
        articulo.descripcion = descripcion;
        articulo.id = id;
        articulos.add(articulo);

      }

      rs.close();
      stmt.close();
    } catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.close();
    }

    return Response.ok(j.toJson(articulos)).build();
  }

  @POST
  @Path("realizar_compra")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response compraArticulo(String json) throws Exception {
    ParamCompraArticulo p = (ParamCompraArticulo) j.fromJson(json, ParamCompraArticulo.class);
    int idArticulo = p.idArticulo;
    int cantidadCompra = p.cantidadCompra;
    Connection conexion = pool.getConnection();

    try {
      conexion.setAutoCommit(false);

      // Obtener la cantidad disponible del artículo
      PreparedStatement stmtCantidad = conexion.prepareStatement(
          "SELECT cantidad FROM articulos WHERE id_articulo = ?");
      stmtCantidad.setInt(1, idArticulo);
      ResultSet rsCantidad = stmtCantidad.executeQuery();

      if (rsCantidad.next()) {
        int cantidadDisponible = rsCantidad.getInt("cantidad");

        if (cantidadCompra > 0 && cantidadCompra <= cantidadDisponible) {
          // Insertar en la tabla "carrito_compra"
          PreparedStatement stmtCompra = conexion.prepareStatement(
              "INSERT INTO carrito_compra(id_articulo, cantidad_compra) VALUES (?, ?)");
          stmtCompra.setInt(1, idArticulo);
          stmtCompra.setInt(2, cantidadCompra);
          stmtCompra.executeUpdate();
          stmtCompra.close();

          // Restar la cantidad comprada del campo "cantidad" de la tabla "articulos"
          PreparedStatement stmtActualizarCantidad = conexion.prepareStatement(
              "UPDATE articulos SET cantidad = cantidad - ? WHERE id_articulo = ?");
          stmtActualizarCantidad.setInt(1, cantidadCompra);
          stmtActualizarCantidad.setInt(2, idArticulo);
          stmtActualizarCantidad.executeUpdate();
          stmtActualizarCantidad.close();

          conexion.commit();
          return Response.ok().build();
        } else {
          return Response.status(400)
              .entity(j
                  .toJson(new Error("La cantidad de compra es inválida o no hay suficiente stock. Cantidad disponible: "
                      + cantidadDisponible)))
              .build();
        }
      } else {
        return Response.status(400).entity(j.toJson(new Error("El artículo no existe"))).build();
      }
    } catch (Exception e) {
      conexion.rollback();
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.setAutoCommit(true);
      conexion.close();
    }
  }

  @POST
  @Path("articulos_carrito")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response obtenerArticulosEnCarrito(String json) throws Exception {
    Connection conexion = pool.getConnection();
    ArrayList<Articulo> articulosEnCarrito = new ArrayList<>();

    try {
      PreparedStatement stmt = conexion.prepareStatement(
          "SELECT cc.id_articulo, cc.cantidad_compra, a.fotografia, a.nombre, a.precio FROM carrito_compra cc INNER JOIN articulos a ON cc.id_articulo = a.id_articulo");
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        int idArticulo = rs.getInt("id_articulo");
        int cantidadCompra = rs.getInt("cantidad_compra");
        byte[] fotografia = rs.getBytes("fotografia");
        String nombre = rs.getString("nombre");
        int precio = rs.getInt("precio");

        Articulo articuloEnCarrito = new Articulo();
        articuloEnCarrito.id = idArticulo;
        articuloEnCarrito.cantidad = cantidadCompra;
        articuloEnCarrito.fotografia = fotografia;
        articuloEnCarrito.nombre = nombre;
        articuloEnCarrito.precio = precio;

        articulosEnCarrito.add(articuloEnCarrito);
      }

      rs.close();
      stmt.close();
    } catch (Exception e) {
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.close();
    }
    return Response.ok(j.toJson(articulosEnCarrito)).build();
  }

  @POST
  @Path("eliminar_articulo_carrito")
  @Produces(MediaType.APPLICATION_JSON)
  public Response eliminarArticuloCarrito(String json) throws Exception {
    ParamEliminaArticulo p = (ParamEliminaArticulo) j.fromJson(json, ParamEliminaArticulo.class);
    int idArticulo = p.idArticulo;

    Connection conexion = pool.getConnection();

    try {
      conexion.setAutoCommit(false);

      // Obtener la cantidad de compra del artículo en el carrito
      PreparedStatement stmtCantidadCompra = conexion.prepareStatement(
          "SELECT cantidad_compra FROM carrito_compra WHERE id_articulo = ?");
      stmtCantidadCompra.setInt(1, idArticulo);
      ResultSet rsCantidadCompra = stmtCantidadCompra.executeQuery();

      int cantidadCompra = 0;

      if (rsCantidadCompra.next()) {
        cantidadCompra = rsCantidadCompra.getInt("cantidad_compra");
      }

      rsCantidadCompra.close();
      stmtCantidadCompra.close();

      // Eliminar el registro del artículo en el carrito
      PreparedStatement stmtEliminarArticulo = conexion.prepareStatement(
          "DELETE FROM carrito_compra WHERE id_articulo = ?");
      stmtEliminarArticulo.setInt(1, idArticulo);
      stmtEliminarArticulo.executeUpdate();
      stmtEliminarArticulo.close();

      // Actualizar la cantidad del artículo en la tabla "articulos"
      PreparedStatement stmtActualizarCantidad = conexion.prepareStatement(
          "UPDATE articulos SET cantidad = cantidad + ? WHERE id_articulo = ?");
      stmtActualizarCantidad.setInt(1, cantidadCompra);
      stmtActualizarCantidad.setInt(2, idArticulo);
      stmtActualizarCantidad.executeUpdate();
      stmtActualizarCantidad.close();

      conexion.commit();
    } catch (Exception e) {
      conexion.rollback();
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.setAutoCommit(true);
      conexion.close();
    }

    return Response.ok().build();
  }

  @POST
  @Path("actualizar_cantidad_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response actualizarCantidadArticulo(String json) throws Exception {
    ParamActualizarCantidadArticulo p = j.fromJson(json, ParamActualizarCantidadArticulo.class);
    int idArticulo = p.idArticulo;
    int nuevaCantidad = p.nuevaCantidad;

    Connection conexion = pool.getConnection();

    try {
      conexion.setAutoCommit(false);

      // Obtener la cantidad actual del artículo en el carrito
      PreparedStatement stmtCantidadCarrito = conexion.prepareStatement(
          "SELECT cantidad_compra FROM carrito_compra WHERE id_articulo = ?");
      stmtCantidadCarrito.setInt(1, idArticulo);
      ResultSet rsCantidadCarrito = stmtCantidadCarrito.executeQuery();

      int cantidadCarrito = 0;

      if (rsCantidadCarrito.next()) {
        cantidadCarrito = rsCantidadCarrito.getInt("cantidad_compra");
      }

      rsCantidadCarrito.close();
      stmtCantidadCarrito.close();

      // Verificar si hay suficiente cantidad disponible en el inventario
      PreparedStatement stmtCantidadArticulo = conexion.prepareStatement(
          "SELECT cantidad FROM articulos WHERE id_articulo = ?");
      stmtCantidadArticulo.setInt(1, idArticulo);
      ResultSet rsCantidadArticulo = stmtCantidadArticulo.executeQuery();

      int cantidadArticulo = 0;

      if (rsCantidadArticulo.next()) {
        cantidadArticulo = rsCantidadArticulo.getInt("cantidad");
      }

      rsCantidadArticulo.close();
      stmtCantidadArticulo.close();

      if (nuevaCantidad > cantidadArticulo + cantidadCarrito) {
        return Response.status(400)
            .entity(j.toJson(new Error("No hay suficiente cantidad disponible en el inventario"))).build();
      }

      // Actualizar la cantidad en el carrito
      PreparedStatement stmtActualizarCantidadCarrito = conexion.prepareStatement(
          "UPDATE carrito_compra SET cantidad_compra = ? WHERE id_articulo = ?");
      stmtActualizarCantidadCarrito.setInt(1, nuevaCantidad);
      stmtActualizarCantidadCarrito.setInt(2, idArticulo);
      stmtActualizarCantidadCarrito.executeUpdate();
      stmtActualizarCantidadCarrito.close();

      // Calcular la diferencia de cantidad en el inventario
      int diferenciaCantidad = nuevaCantidad - cantidadCarrito;

      if (diferenciaCantidad != 0) {
        // Actualizar la cantidad en la tabla "articulos"
        PreparedStatement stmtActualizarCantidadArticulos = conexion.prepareStatement(
            "UPDATE articulos SET cantidad = cantidad - ? WHERE id_articulo = ?");
        stmtActualizarCantidadArticulos.setInt(1, diferenciaCantidad);
        stmtActualizarCantidadArticulos.setInt(2, idArticulo);
        stmtActualizarCantidadArticulos.executeUpdate();
        stmtActualizarCantidadArticulos.close();
      }

      conexion.commit();

    } catch (Exception e) {
      conexion.rollback();
      return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
    } finally {
      conexion.setAutoCommit(true);
      conexion.close();
    }

    return Response.ok().build();

  }

}
