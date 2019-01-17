package solver;

import java.util.Set;
import java.util.stream.Collectors;

import model.Allocation;
import model.AllocationImpl;
import model.User;

public class UserResourceInstanceAllocation implements Allocation<User,ResourceInstance> {
	
	private final AllocationImpl<User, ResourceInstance> al;
	private UserResourceInstanceAllocation(User u, ResourceInstance ri) {
		al = AllocationImpl.newInstance(u, ri);
	}

	public static UserResourceInstanceAllocation newInstance(User u, ResourceInstance ri)
	{
		return new UserResourceInstanceAllocation(u, ri);
	}
	
	public boolean equals(Object o)
	{
		return ((UserResourceInstanceAllocation)o).al.equals(al);
	}
	
	public int hashCode()
	{
		return al.hashCode();
	}
	
	public String toString()
	{
		return al.toString();
	}
	
	public ResourceInstance getResource()
	{
		return al.getResource();
	}

	public User getUser() {
		return al.getUser();
	}

	public static Set<UserResourceInstanceAllocation> newInstances(User u, Set<ResourceInstance> resources) {
		return resources.stream().map(x->UserResourceInstanceAllocation.newInstance(u, x))
				.collect(Collectors.toSet());
	}
}
