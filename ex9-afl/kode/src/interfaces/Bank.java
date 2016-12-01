package interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Bank extends Remote 
{
    /**
     * Returns an existing Account if one with that name exists; otherwise
     * creates a new Account
     */
	public Account getAccount(String name)
            throws RemoteException;
}

