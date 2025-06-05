package Master;

/**
 * Classe di entry-point che avvia il Master.
 */
public class MasterMain {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java Master.MasterMain <porta>");
            System.exit(1);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            System.err.println("Porta non valida: " + args[0]);
            System.exit(1);
            return;
        }

        MasterServer server = new MasterServer(port);
        server.start();
    }
}
