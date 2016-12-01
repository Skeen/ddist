import java.net.*;
import java.io.*;

/**
 * The WebServer class has the responsability of accepting requests.
 * Essencially this means, that it just opens a port and listens for
 * connections, and when a client connect it redirects this client to a
 * WebServerThread, which serves it.
 *
 * The design is in no way optimized for performence, but is designed for
 * readablity.
 */
public class WebServerTester 
{
    // The port we're going to serve.
    private int portNumber = 80;  
    // The socket we're going to be listening on.
    private ServerSocket serverSocket;

    /**
     * Will print out the IP address of the local host and the port on which this
     * server is accepting connections. 
     */
    private void printLocalHostAddress() 
    {
        try 
        {
            InetAddress localhost = InetAddress.getLocalHost();
            String localhostAddress = localhost.getHostAddress();
            System.out.println("Contact this server on the IP address " + localhostAddress);
        }
        catch (UnknownHostException e) 
        {
            System.err.println("Cannot resolve the Internet address of the local host.");
            System.err.println(e);
            System.exit(-1);			
        }
    }

    /**
     * Will register this server on the port number portNumber.
     * Will not start waiting for connections.
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
     * Will deregister this server and close it down.
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
     * Waits for the next client to connect on port number portNumber or takes the 
     * next one in line in case a client is already trying to connect.
     * @return The socket of the connection, null if there were any failures.
     */
    private Socket waitForConnectionFromClient() 
    {
        Socket res = null;
        try 
        {
            res = serverSocket.accept();
        }
        catch (IOException e) 
        {
            // We just return null on IOExceptions
        }
        return res;
    }

    /**
     * Start up the server; listening for requests and redirecting them to
     * workers. Notice: This function never returns.
     */
    public void run() 
    {
        System.out.println("WebServer Started!");
        printLocalHostAddress();
        registerOnPort();

        // Wait for the first connection before starting to count!
        Socket socket = waitForConnectionFromClient();

        // Start the clock!
        long currentTime = System.currentTimeMillis();

        // Run for 10 seconds!
        while (System.currentTimeMillis() < currentTime + 100000) 
        {
            socket = waitForConnectionFromClient();
            if(socket == null)
            {
                continue;
            }
            WebServerThread thread = new WebServerThread(socket);
            // for multithreaded webserver use;
            thread.start();
            // for single threaded webserver use;
            ////thread.run();
        }

        deregisterOnPort();
    }

    public static void main(String[] args)
    {
        // Create a webserver and run it.
        WebServerTester server = new WebServerTester();
        server.run();
    }
}
