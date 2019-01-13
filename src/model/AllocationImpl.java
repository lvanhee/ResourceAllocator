package model;

public class AllocationImpl<T> implements Allocation<T> {
	private final T receiver;
	private final Resource resource;
	
	public AllocationImpl(T pl, Resource role) {
		this.receiver = pl;
		this.resource = role;
	}
	
	public int hashCode()
	{
		int res = receiver.hashCode()+resource.hashCode();
		return res;
	}
	
	public boolean equals(Object o)
	{
		AllocationImpl<T> a = (AllocationImpl<T>)o;
		return a.receiver.equals(receiver) && a.resource.equals(resource);
	}
	
	public String toString()
	{
		return receiver+":"+resource;
	}

	public static<T> AllocationImpl<T> newInstance(T usergroup, Resource resource) {
		return new AllocationImpl<T>(usergroup, resource);
	}

	public Resource getResource()
	{
		return resource;
	}

	public T getUser() {
		return receiver;
	}
}
