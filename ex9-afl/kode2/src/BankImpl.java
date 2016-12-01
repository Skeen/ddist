import java.util.Map;

public class BankImpl implements Bank 
{
    private final Map<String, Account> accounts;

    public BankImpl(Map<String, Account> s)
    {
        accounts = s;
    }

    public String getAccount(String name)
    {
        Account a = accounts.get(name);
        if( a == null)
        {
            a = new AccountImpl(name);
            accounts.put(name, a);
        }
        return name;
    }
}
