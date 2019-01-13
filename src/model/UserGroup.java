package model;
import java.util.Set;

public class UserGroup {
	
	private final Set<User>users;

	private UserGroup(Set<User> users) {
		this.users = users;		
	}

	public static UserGroup newInstance(Set<User> users) {
		return new UserGroup(users);
	}

	public Set<User> getUsers() {
		return users;
	}
	
	public String toString()
	{
		return users.toString();
	}
	
	public int hashCode()
	{
		return users.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((UserGroup)o).users.equals(users);
	}

}
