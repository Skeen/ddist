import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * The WebServer class has the responsibility of accepting requests.
 * Essentially this means, that it just opens a port and listens for
 * connections, and when a client tries to connect, this Server
 * starts a WebServerThread with the responsibility of serving the
 * client with responses.
 *
 * The design is in no way optimized for performance, but is designed for
 * readability.
 */
public class WebServer
{
    // The port we're going to serve.
    private final static int portNumber = 40404;
    // The socket we're going to be listening on.
    private ServerSocket serverSocket;

    /**
     * Prints out the IP address of the local host and the port on which this
     * server is accepting connections. 
     */
    private void printLocalHostAddress()
    {
        try 
        {
            InetAddress localhost = InetAddress.getLocalHost();
            String localhostAddress = localhost.getHostAddress();
            System.out.println("Contact this server on the IP address " + localhostAddress + ":" + portNumber);
        }
        catch (UnknownHostException e) 
        {
            System.err.println("Cannot resolve the Internet address of the local host.");
            System.err.println(e);
            System.exit(-1);			
        }
    }

    /**
     * Registers this server on the port number portNumber.
     * Does not start waiting for connections.
     * For this you should call waitForConnectionFromClient().
     */
    private void registerOnPort() 
    {
        // Opens the server socket on a specific port.
        try 
        {
            serverSocket = new ServerSocket(portNumber);
        }
        catch (IOException e) 
        {
            serverSocket = null;
            System.err.println("Cannot open server socket on port number" + portNumber);
            System.err.println(e);
            System.exit(-1);			
        }
    }

    /**
     * Deregisters this server and closes it down.
     */
    public void deregisterOnPort() 
    {
        // The server is automatically deregistered when we shutdown the socket
        // using close.
        if (serverSocket != null) 
        {
            try 
            {
                serverSocket.close();
                serverSocket = null;
            }
            catch (IOException e) 
            {
                System.err.println(e);
            }
        }
    }

    /**
     * Starts up the server, listens for requests and redirects them to
     * workers(WebServerThreads).
     * Notice: This function never returns.
     */
    public void run() 
    {
        System.out.println("WebServer Started!");
        printLocalHostAddress();
        registerOnPort();

        // The server just waits, listening to the socket for a client to make
        // a connection request.
        while (true)
        {
            Socket client;
            // Listens for a connection to be made to this socket and accepts
            // it.
            // The method blocks until a connection is made.
            try
            {
                client = serverSocket.accept();
            }
            catch (IOException e)
            {
                // Something went wrong. Just continue waiting for new
                // connection attempts.
                continue;
            }

            // Let a new WebServerThread serve the client.
            WebServerThread thread = new WebServerThread(client);
            // for multithreaded web server use;
            thread.start();
            // for single threaded web server use;
            //thread.run();
        }
        /*
        deregisterOnPort();
        System.out.println("Goodbye, world!");
        */
    }

    public static void main(String[] args)
    {
        // Create a web server and run it.
        WebServer server = new WebServer();
        server.run();
    }
}
