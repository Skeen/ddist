import replicated_calculator.*;
import pointtopointqueue.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.math.BigInteger;


/**
 * 
 * Rudimentary implementation of a Textual User Interface for a 
 * DistributedCalculator client.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 *
 */

public class ServerTUI 
{	
    public static void main(String[] args) 
	{
		/**
		 * Here you would instantiation your own, more impressive server instead.
		 */
		Server server = new ServerStandalone();
	
		try 
		{
			// For reading from standard input
			BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
			String s;
			
			System.out.println("Exit: Gracefully logs out");
			System.out.println("Crash: Makes the server crash.");
			System.out.println("");
			
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
			
			if (s.equals("")) 
			{
				server.createGroup(Parameters.getServerPortForServers());
			} 
			else 
			{
				server.joinGroup(new InetSocketAddress(serverAddress,Parameters.getServerPortForServers()));
			}
			
			while ((s = stdin.readLine()) != null) 
			{ 
				if (s.equals("Crash")) 
				{
					System.exit(-1);
				} 
				else if (s.equals("Exit")) 
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