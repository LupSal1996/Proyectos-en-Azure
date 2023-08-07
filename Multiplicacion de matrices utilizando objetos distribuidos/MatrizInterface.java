import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MatrizInterface extends Remote {
    public float[][] multiplica_matrices(float[][] matrizA, float[][] matrizB) throws RemoteException;
}
