package model;

public class ResourceType {
	
	private final String name;

	private ResourceType(String name) {
		this.name = name;
	}

	public static ResourceType newInstance(String name) {
		return new ResourceType(name);
	}
	
	public int hashCode()
	{
		return name.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((ResourceType)o).name.equals(name);
	}
	
	public String toString()
	{
		return name;
	}

}
