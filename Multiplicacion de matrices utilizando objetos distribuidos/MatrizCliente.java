import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

class MultiplicacionThread extends Thread {
    float[][] A;
    float[][] B;
    public float[][] matriz;
    MatrizInterface server;

    public MultiplicacionThread(float[][] A, float[][] B, MatrizInterface server) {
        this.A = A;
        this.B = B;
        this.server = server;
    }

    public void run() {
        try {
            matriz = server.multiplica_matrices(this.A, this.B);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public float[][] getMatriz() {
        return matriz;
    }
}

public class MatrizCliente {

    public static void main(String[] args)
            throws MalformedURLException, RemoteException, NotBoundException, InterruptedException {
        // Obtener los valores de N y M como argumentos de línea de comandos
        int N = Integer.parseInt(args[0]);
        int M = Integer.parseInt(args[1]);

        // Crear las matrices A y B con los valores iniciales dados
        float[][] A = new float[N][M];
        float[][] B = new float[M][N];

        // Obtener una instancia del objeto remoto del servidor 0
        String url0 = "rmi://localhost/MatrizServer";
        MatrizInterface server0 = (MatrizInterface) Naming.lookup(url0);

        // Obtener una instancia del objeto remoto del servidor
        String url1 = "rmi://10.0.0.5/MatrizServer";
        MatrizInterface server1 = (MatrizInterface) Naming.lookup(url1);

        // Obtener una instancia del objeto remoto del servidor
        String url2 = "rmi://10.0.0.6/MatrizServer";
        MatrizInterface server2 = (MatrizInterface) Naming.lookup(url2);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                A[i][j] = 2 * i + 3 * j;
            }
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                B[i][j] = 3 * i - 2 * j;
            }
        }

        // Llamar al método divideMatriz para dividir las matrices A y BT
        float[][][] Ap = divideMatriz(A);
        float[][][] BTp = divideMatriz(transpuesta(B));

        float[][][] resultado = new float[Ap.length * Ap.length][Ap[0].length][Ap[0].length];

        // Crear un arreglo de hilos para almacenar cada hilo
        Thread[][] hilos = new Thread[Ap.length][BTp.length];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < BTp.length; j++) {
                MultiplicacionThread hilo = new MultiplicacionThread(Ap[i], BTp[j], server0);
                hilos[i][j] = hilo;
                hilo.start();
            }
        }

        for (int i = 3; i < 6; i++) {
            for (int j = 0; j < BTp.length; j++) {
                MultiplicacionThread hilo = new MultiplicacionThread(Ap[i], BTp[j], server1);
                hilos[i][j] = hilo;
                hilo.start();
            }
        }

        for (int i = 6; i < Ap.length; i++) {
            for (int j = 0; j < BTp.length; j++) {
                MultiplicacionThread hilo = new MultiplicacionThread(Ap[i], BTp[j], server2);
                hilos[i][j] = hilo;
                hilo.start();
            }
        }

        // Esperar a que todos los hilos terminen
        for (int i = 0; i < Ap.length; i++) {
            for (int j = 0; j < BTp.length; j++) {
                hilos[i][j].join(); // Esperar a que el hilo termine
            }
        }

        for (int i = 0; i < Ap.length; i++) {
            for (int j = 0; j < BTp.length; j++) {
                float[][] multiplicacion = ((MultiplicacionThread) hilos[i][j]).getMatriz();
                for (int k = 0; k < multiplicacion.length; k++) {
                    for (int l = 0; l < multiplicacion[0].length; l++) {
                        resultado[i * 9 + j][k][l] = multiplicacion[k][l];
                    }
                }
            }
        }

        // Llamar al método unirMatrices para unir matrices multiplicadas
        float[][] C = unirMatrices(resultado);

        // Realiza checksum
        if (N == 9 && M == 4) {
            System.out.println("Matriz C:");
            imprimirMatriz(C);

            double checksum = 0.0;
            for (int i = 0; i < C.length; i++) {
                for (int j = 0; j < C[0].length; j++) {
                    checksum += C[i][j];
                }
            }
            System.out.println("Checksum: " + checksum);
        } else if (N == 900 && M == 400) {

            double checksum = 0.0;
            for (int i = 0; i < C.length; i++) {
                for (int j = 0; j < C[0].length; j++) {
                    checksum += C[i][j];
                }
            }
            System.out.println("Checksum: " + checksum);
        }
    }

    // Método para dividir una matriz en submatrices
    public static float[][][] divideMatriz(float[][] matriz) {
        int n = matriz.length;
        int m = matriz[0].length;
        float[][][] submatrices = new float[9][n / 9][m];

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                for (int k = 0; k < n / 9; k++) {
                    for (int l = 0; l < m; l++) {
                        submatrices[i][k][l] = matriz[i * (n / 9) + k][l];
                    }
                }
            }
        }
        return submatrices;
    }

    // Método para obtener la transpuesta de una matriz
    public static float[][] transpuesta(float[][] matriz) {
        int n = matriz.length;
        int m = matriz[0].length;
        float[][] resultado = new float[m][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                resultado[j][i] = matriz[i][j];
            }
        }
        return resultado;
    }

    // Método para imprimir una matriz
    public static void imprimirMatriz(float[][] matriz) {
        int n = matriz.length;
        int m = matriz[0].length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                System.out.print(matriz[i][j] + " ");
            }
            System.out.println();
        }
    }

    // Método para unir las matrices multiplicadas en una sola matriz C
    public static float[][] unirMatrices(float[][][] matrices) {
        int n = matrices[0].length; // Se asume que todas las matrices tienen las mismas dimensiones
        int m = matrices[0][0].length;
        float[][] resultado = new float[n * 9][m * 9]; // La matriz C tendrá dimensiones N*9 x M*9

        // Recorremos las matrices multiplicadas en el arreglo de 3 dimensiones
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                // Copiamos los valores de la matriz multiplicada actual en la matriz resultado
                for (int k = 0; k < n; k++) {
                    for (int l = 0; l < m; l++) {
                        resultado[i * n + k][j * m + l] = matrices[i * 9 + j][k][l];
                    }
                }
            }
        }
        return resultado;
    }

}
