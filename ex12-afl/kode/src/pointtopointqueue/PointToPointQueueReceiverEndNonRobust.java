package pointtopointqueue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.*;
import java.net.*;

/**
 *
 * Non-robust implementation of the receiving end of a distributed
 * queue of objects of class E. The class E must be Serializable, as
 * the objects are moved using ObjectOutputStream and
 * ObjectInputStream. The sending end is implemented by
 * ObjectQueueSenderEndNonRobust.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */

@SuppressWarnings({"ALL"})
public class PointToPointQueueReceiverEndNonRobust<E extends Serializable>
    extends Thread 
    implements PointToPointQueueReceiverEnd<E> 
{

    public PointToPointQueueReceiverEndNonRobust()
    {
        this.pendingObjects = new ConcurrentLinkedQueue<E>();
    }

    /**
     * Specifies the port on which this receiving end is listening.
     * 
     * @param port The port number on which this receiving end is
     *             waiting for connections.
     * @throws IOException when it cannot open the server socket on
     *         the given port.
     */
    public int listenOnPort(int port) throws IOException 
    {
        this.serverSocket = new ServerSocket(port);
        this.start();
        return this.serverSocket.getLocalPort();
    }

    /**
     * Calling this method will make the queue stop receiving incoming
     * messages.  Should only be done when the sending ends no longer
     * try to send messages to this queue. If the sending end try to
     * send to this queue after a call to shutdown() they will get a
     * connection error.
     */
    public void shutdown() 
    {
        synchronized (pendingObjects) 
        {
            shutdown = true;
            pendingObjects.notifyAll();
        }
    }

    /**
     * Will return the next object in this incoming queue. If the
     * queue is empty, then the method blocks until incoming objects
     * arrive. Removes the object from the queue.  If the queue is
     * dead (shut down and has delivered all objects), then get()
     * will return null.
     * 
     * @return The front of the queue, null if the queue is dead.
     */
    public E get() 
    {		
        waitForObjectsToBePendingOrAShutdown();
        synchronized (pendingObjects) 
        {
            if (pendingObjects.isEmpty()) 
            {
                return null;
            }
            else
            {
                return pendingObjects.poll();
            }
        }
    }

    /**
     * We send to ourself by simply adding the object to the
     * queueu. We notify that there are new messages.
     */    
    public void put(E object) 
    {
        synchronized (pendingObjects) 
        {
            pendingObjects.add(object);
            pendingObjects.notify();
        }
    }

    /**
     * Starts a thread which waits for incoming objects and adds them
     * to this queue, so they can be retrieved using poll(). Stops
     * after shutdown() is called.
     */
    public void run() 
    {
        while (!shutdown) 
        {
            pullObject();
        }
        try 
        {
            serverSocket.close();
        }
        catch (IOException _) 
        {
            // IGNORE AND CLOSE
        }
    }

    /**
     * The queue of received objects which were not yet delivered.
     */
    final private ConcurrentLinkedQueue<E> pendingObjects; 

    /**
     * The serverSocket on which this receiving end is listening for
     * incoming connections.
     */
    private ServerSocket serverSocket;

    /**
     * Used to signal that the queue should stop taking incoming
     * messages.
     */
    private boolean shutdown;

    /**
     * Internal method for pulling an object from the sending end(s)
     * of the queue.
     */
    @SuppressWarnings("unchecked")
    private void pullObject() 
    {
        Socket socket = null;
        ObjectInputStream forReceivingObjects = null;		
        try 
        {
            // Set 1000 milliseconds timeout to come back to live if
            // listening after a shutdown
            serverSocket.setSoTimeout(1000); 
            while (socket==null && !shutdown) 
            {
                try 
                {
                    socket = serverSocket.accept();
                }
                catch (SocketTimeoutException _) 
                {
                    // Ignore
                }
            }
        }
        catch (IOException e) 
        {
            log("Problems accepting incoming connections!");
            log(e);
            return;
        }
        // Check if the loop returned because we are shutting down
        if (socket == null) 
        {
            return;
        }
        try 
        {
            forReceivingObjects 
                = new ObjectInputStream(socket.getInputStream()); 
        }
        catch (IOException e) 
        {
            log("Problems accepting incoming connections!");
            log(e);
            try 
            {
                socket.close();
            }
            catch (IOException ee)
            {
                log(ee);
            }
            return;
        }
        E object = null;
        try 
        {
            Object incomingObject = forReceivingObjects.readObject();
            object = (E)(incomingObject); 
        }
        catch (ClassCastException e) 
        {
            log("The peer sent object of wrong type on " + socket);
            log(e);
            object = null;
        }
        catch (IOException e) 
        {
            log("Problems receiving object on " + socket + " [" + e + "]");
            object = null;
        }
        catch (ClassNotFoundException e) 
        {
            log("The peer sent object of unknown type on " + socket);
            log(e);
            object = null;
        } 
        if (object != null) 
        {
            synchronized (pendingObjects) 
            {
                pendingObjects.add(object);
                pendingObjects.notify();
            }
        } 
        try 
        {
            forReceivingObjects.close();
        }
        catch (IOException ee) 
        {
            log(ee);
        }
        try 
        {
            socket.close();
        }
        catch (IOException ee) 
        {
            log(ee);
        }
    }

    /**
     * Used by callers to wait for objects to enter the queue of pending
     * deliveries. When the method returns, then either the queue of pending
     * deliveries is non-empty, or the queue is shutting down. Both might be 
     * the case.
     */
    private void waitForObjectsToBePendingOrAShutdown() 
    {
        synchronized (pendingObjects) 
        {
            while (pendingObjects.isEmpty() && !shutdown) 
            {
                try 
                {
                    // We will be woken up if an object arrives or the
                    // queue is shut down.
                    pendingObjects.wait();
                }
                catch (InterruptedException e) 
                {
                    // Probably shutting down. The while condition
                    // will ensure proper behavior in case of some
                    // other interruption.
                }
            }
            // Now: pendingObjects is non empty or we are shutting
            // down
        }
    }

    /***
     ** HELPERS FOR DEBUGGING
     **/
    protected boolean log = false;

    public void printLog() 
    {
        log = true;
    }

    protected void log(String msg) 
    {
        if (log) 
        {
            System.out.println(this + " said: " + msg);
        }
    }

    protected void log(Exception e) 
    {
        if (log)
        {
            System.out.println(this + " cast: " + e);
        }
    }
}
