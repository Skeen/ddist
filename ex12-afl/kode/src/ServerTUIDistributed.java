import replicated_calculator.*;
import pointtopointqueue.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.math.BigInteger;

public class ServerTUIDistributed 
{
    synchronized public static void report(String str) 
	{
		System.out.println(str);
    }
	
    public static void main(String[] args) 
	{
		/**
		 * Here you would instantiation your own, more impressive server instead.
		 */
		Server server = new ServerDistributed();
        // Ports are; Serv to Client, Client to serv, Serv to Serv.
        Parameters.setPorts(0,0,0);
		try 
		{
			// For reading from standard input
			BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
			String s;
			
			report("Exit: Gracefully logs out");
			report("Crash: Makes the server crash.");
			report("");
			
			/*
			 * Get the address of a server.
			 */
			String serverAddress = null;
			System.out.print("Enter address of another server (ENTER for standalone): ");
			if ((s = stdin.readLine()) != null) 
			{
				serverAddress = s;
			} 
			else 
			{
				return;
			}
            
			System.out.println();
            if (s.equals("")) 
			{
                System.out.println("Created server group");
				server.createGroup(Parameters.getServerPortForServers());
                System.out.println("Created server group at: "+InetAddress.getLocalHost()+":"+Parameters.getServerPortForServers());
			} 
			else 
			{
                System.out.println("Joining server group");
                int colon = serverAddress.indexOf(":");
                String hostString = serverAddress.substring(0, colon);
                String portString = serverAddress.substring(colon+1);
                Integer ports = Integer.valueOf(portString);
				server.joinGroup(new InetSocketAddress(hostString,ports));
                System.out.println("Joined server group at: "+InetAddress.getLocalHost()+":"+Parameters.getServerPortForServers());
			}
            
            System.out.println("Listening for clients at: "+InetAddress.getLocalHost()+":"+Parameters.getServerPortForClients());
            
            System.out.println("Server running");

			while ((s = stdin.readLine()) != null) 
			{ 
				if (s.equals("Crash")) 
				{
					System.exit(-1);
				} 
				else if (s.equals("Exit") || s.equals("exit")) 
				{
					server.leaveGroup();
					break;
				} 
			}
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		System.out.println("Shutting down the server!");
    }
}
