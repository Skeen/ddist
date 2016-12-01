package interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Each Account maintains a balance that may be raised due to depsoits and
 * lowered due to withdrawls
 */
public interface Account extends Remote 
{
	public String getName()
        throws RemoteException;
    public double getBalance()
        throws RemoteException;
    public void deposit(double amount)
        throws RemoteException;
    public void withdraw(double amount)
        throws RemoteException;
}

