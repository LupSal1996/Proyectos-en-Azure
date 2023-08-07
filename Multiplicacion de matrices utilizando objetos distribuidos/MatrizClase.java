import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MatrizClase extends UnicastRemoteObject implements MatrizInterface {
    public MatrizClase() throws RemoteException {
        super();
    }

    // Método para multiplicar dos matrices
    public float[][] multiplica_matrices(float[][] matrizA, float[][] matrizBT) throws RemoteException {
        int n = matrizA.length;
        int m = matrizA[0].length;

        float[][] resultado = new float[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                resultado[i][j] = 0;
                for (int k = 0; k < m; k++) {
                    resultado[i][j] += matrizA[i][k] * matrizBT[j][k];
                }
            }
        }
        return resultado;
    }
}
