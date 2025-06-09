package Master;

/**
 * Classe di entry-point che avvia il Master.
 * Contiene il metodo `main` usato per avviare il server Master su una porta specificata da linea di comando.
 */
public class Master {

    public static void main(String[] args) {
        // Controlla che ci sia esattamente un argomento (la porta)
        if (args.length != 1) {
            System.err.println("Uso: java Master.MasterMain <porta>");
            // Termina il programma con codice di errore
            System.exit(1);
        }
        int port;
        try {
            // Converte l'argomento da stringa a intero (porta)
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            // Se la conversione fallisce, l'argomento non è una porta valida --> termina con errore
            System.err.println("Porta non valida: " + args[0]);
            System.exit(1);
            return;
        }
        // Crea un'istanza del server Master con la porta specificata e avvia il server
        MasterServer server = new MasterServer(port);
        server.start();
    }
}
