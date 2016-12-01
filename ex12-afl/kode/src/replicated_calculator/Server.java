package replicated_calculator;
import java.io.*;
import java.net.InetSocketAddress;

/**
 * 
 * An interface for a replicated calculator server. Has methods for founding a 
 * server group, joining a server group and leaving a server group.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public interface Server
{
	/**
	 * Used by the first group member.
	 * Specifies the port on which this founding peer is listening for peers.
	 * 
	 * @param port The port number on which this founding peer is waiting for peers.
	 * @throws IOException in case there are problems with getting that port.
	 */
	public void createGroup(int port) throws IOException;
	
	/**
	 * Used to join a peer group. This takes place by contacting one
	 * of the existing peers of the peer group. After this call the
	 * current instance of MulticastQueue is considered part of the 
	 * peer group. 
	 * 
	 * @param serverAddress The IP address and port of the known peer.
	 */
	public void joinGroup(InetSocketAddress knownPeer);

	/**
	 * Makes this instance of MulticastQueue leave the peer group. The other 
	 * peers should be informed of this. This instance of MulticastQueue should
	 * keep receiving messages until it has officially left the group and should,
	 * if needed, keep participating in implementing the distributed queue until 
	 * it has officially left the group.
	 */
	public void leaveGroup();
	
	
}
