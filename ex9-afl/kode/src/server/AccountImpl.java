package server;

import java.rmi.RemoteException;
import interfaces.Account;

import java.io.Serializable;

public class AccountImpl implements Account, Serializable 
{
    private static final long serialVersionUID = 227L;
    private String name;
    private double balance;

    public AccountImpl(String name)
    {
        this.name = name;
    }

    public String getName()
        throws RemoteException
    {
        return name;
    }

    public double getBalance()
        throws RemoteException
    {
        return balance;
    }

    public void deposit(double amount)
        throws RemoteException
    {
        assert(amount < 0);

        balance = balance + amount;
    }

    public void withdraw(double amount)
        throws RemoteException
    {
        assert(amount < 0);
        
        balance = balance - amount;
    }
}
