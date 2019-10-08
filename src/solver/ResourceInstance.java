package solver;

import java.util.HashSet;
import java.util.Set;

import model.ResourceType;
import model.UserResourceTypeAllocation;

public class ResourceInstance {
	
	private final ResourceType type;
	private final int instanceNumber;

	private ResourceInstance(ResourceType instanceType, int instanceNumber) {
		this.type = instanceType;
		this.instanceNumber = instanceNumber;
	}
	
	public int hashCode()
	{
		return type.hashCode()+instanceNumber;
	}
	
	public boolean equals(Object o)
	{
		ResourceInstance ri = ((ResourceInstance)o);
		return ri.type.equals(type)&&ri.instanceNumber==instanceNumber;
	}

	public static Set<ResourceInstance> newInstancesFrom(ResourceType x, 
			int nbAllocableSlots) 
	{
		Set<ResourceInstance> res = new HashSet<>();
		for(int i = 0; i < nbAllocableSlots ; i++)
			res.add(ResourceInstance.newInstance(x, i));
		return res;
	}
	
	public String toString()
	{
		return type+"#"+instanceNumber;
	}

	public static ResourceInstance newInstance(ResourceType x, int i) {
		return new ResourceInstance(x,i);
	}

	public ResourceType getResourceType() {
		return type;
	}

	public int getInstanceNumber() {
		return instanceNumber;
	}

}
