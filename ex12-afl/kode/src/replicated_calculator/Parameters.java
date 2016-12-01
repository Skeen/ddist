package replicated_calculator;

public class Parameters 
{
    // Nullpointer exceptions if not set prior to use.
	private static int serverPortForClients = 40489; 
	private static int clientPortForServer = 41489; 
	private static int serverPortForServers = 42489; 
	
    /**
     * Set our ports for use in calc program
     * Does not set a port if the given parameter for it is less than zero.
     */
    public static void setPorts(int serverClientPort, int clientServerPort, int serverServerPort)
    {
        if(serverClientPort >= 0)
            serverPortForClients = serverClientPort;
        if(clientServerPort >= 0)
            clientPortForServer = clientServerPort;
        if(serverServerPort >= 0)
            serverPortForServers = serverServerPort;
    }
    
    public static int getServerPortForClients()
    {
        return serverPortForClients;
    }
    
    public static int getClientPortForServer()
    {
        return clientPortForServer;
    }
    
    public static int getServerPortForServers()
    {
        return serverPortForServers;
    }
}
