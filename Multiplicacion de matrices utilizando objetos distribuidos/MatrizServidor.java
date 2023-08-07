import java.rmi.Naming;

public class MatrizServidor {
    public static void main(String[] args) {
        try {
           String url = "rmi://localhost/MatrizServer";
           
            MatrizInterface matriz = new MatrizClase();
            Naming.rebind(url, matriz);
            System.out.println("Servidor RMI listo.");
        } catch (Exception e) {
            System.err.println("Error en el servidor RMI: " + e);
            e.printStackTrace();
        }
    }
}
