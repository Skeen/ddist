package server;

import java.rmi.RemoteException;
import interfaces.Bank;
import interfaces.Account;

import java.util.Map;
import java.util.HashMap;

public class BankImpl implements Bank 
{
    private Map<String, Account> accounts;

    public BankImpl()
    {
        accounts = new HashMap<String, Account>();
    }

    public Account getAccount(String name)
        throws RemoteException
    {
        Account a = accounts.get(name);
        if( a == null)
        {
            a = new AccountImpl(name);
        }
        return a;
    }
}
