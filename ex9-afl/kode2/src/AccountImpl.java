public class AccountImpl implements Account
{
    private final String name;
    private double balance;

    public AccountImpl(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public double getBalance()
    {
        return balance;
    }

    private void depositWorker(double amount)
    {
        balance = balance + amount;
    }

    public void deposit(String amount)
    {
        double d = Double.valueOf(amount.trim());
        depositWorker(d);
    }
     
    private void withdrawWorker(double amount)
    {
        balance = balance - amount;
    }

    public void withdraw(String amount)
    {
        double d = Double.valueOf(amount.trim());
        withdrawWorker(d);
    }
}
