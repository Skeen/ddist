package multicastqueue;

import java.net.InetSocketAddress;
import java.io.IOException;

/**
 * 
 * A test run of PointToPointQueueSenderEndNonRobust and
 * PointToPointQueueReceiverEndNonRobust.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
class PointToPointQueueNonRobustTest {

	/**
	 * Will open three sending ends and one receiving end, which
	 * serves them all at the same time.  We check if all messages
	 * arrive exactly once.
	 */
	public static void main(String[] args) throws IOException {
	        final int testSize = 500;

		PointToPointQueueReceiverEnd<Integer> receiver 
		    = new PointToPointQueueReceiverEndNonRobust<Integer>();
		receiver.listenOnPort(40999);
		PointToPointQueueSenderEnd<Integer> sender1 
		    = new PointToPointQueueSenderEndNonRobust<Integer>();
		sender1.setReceiver(new InetSocketAddress("localhost",40999));
		PointToPointQueueSenderEnd<Integer> sender2 
		    = new PointToPointQueueSenderEndNonRobust<Integer>();
		sender2.setReceiver(new InetSocketAddress("localhost",40999));
		PointToPointQueueSenderEnd<Integer> sender3 
		    = new PointToPointQueueSenderEndNonRobust<Integer>();
		sender3.setReceiver(new InetSocketAddress("localhost",40999));
		
		// Let them all send at the same time and mix it with
		// local puts.
		for (int i=0; i<testSize; i++) {
			if (i % 4 == 0) {
				sender1.put(Integer.valueOf(i));
			} else if (i % 4 == 1) {
				sender2.put(Integer.valueOf(i));
			} else if (i % 4 == 2) {
				sender3.put(Integer.valueOf(i));		
			} else {
			        receiver.put(Integer.valueOf(i));
			}
		}

		boolean received[] = new boolean[testSize];
		
		for (int i=0; i<testSize; i++) {
			Integer x = receiver.get();
			if (received[x.intValue()]==true) {
				System.err.println("Received " 
						   + x  + " twice!");
				System.exit(-1);
			} else {
				received[x.intValue()]=true;
			}
		}
		for (int i=0; i<testSize; i++) {
			if (received[i]==false) {
				System.err.println("Did not receiver " + i);
				System.exit(-1);
			} 
		}
		System.out.println("OK!");
		
		receiver.shutdown();
		sender1.shutdown();
		sender2.shutdown();
		sender3.shutdown();
	}
}
