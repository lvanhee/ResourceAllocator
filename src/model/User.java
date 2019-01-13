package model;

public class User {
	
	private final String name;
	
	private User(String name2) {
		this.name = name2;
	}

	public static User newInstance(String name)
	{
		return new User(name);
	}
	
	public int hashCode()
	{
		return name.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((User)o).name.equals(name);
	}
	
	public String toString()
	{
		return name;
	}

}
