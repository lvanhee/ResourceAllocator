package output;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import input.ProblemInstance;
import model.ResourceType;
import model.User;
import model.UserResourceTypeAllocation;
import solver.ResourceInstance;
import solver.UserResourceInstanceAllocation;
import model.UserGroup;

public class Printer {

	public static void printOutput(Set<UserResourceInstanceAllocation> s, 
			Map<UserResourceInstanceAllocation, Integer> prefsPerAllocation) {
		/*for(Allocation a:s)
			System.out.println(a+" with preference rank:"+(prefsPerAllocation.get(a)));*/
		
		System.out.println("\n\nGeneral stats:");
		int maxUnhapiness = prefsPerAllocation.keySet().stream()
				.filter(x->s.contains(x))
				.map(x->prefsPerAllocation.get(x)).max(Integer::compareTo).get();
		for(int i = 0; i <= maxUnhapiness; i++)
		{
			final int count = i;
			System.out.println("# of rank "+(i)+"->"+s.stream().filter(x->prefsPerAllocation.get(x).equals(count)).count());
		}
		
	}

	public static void processResults(Set<UserResourceInstanceAllocation>res, 
		ProblemInstance inF
			) {
		List<UserResourceInstanceAllocation> sortedList = new LinkedList<>();
		sortedList.addAll(res);
		Collections.sort(sortedList, (x,y)-> x.getUser().toString()
				.compareTo(y.getUser().toString()));
		
		for(UserResourceInstanceAllocation a:sortedList)
			System.out.println(a+" rank:"+
		inF.getAllocationsPerResourceInstance().get(a));
		
		System.out.println("\n\n");
		
		Set<ResourceInstance> allocatedResources = 
				sortedList.stream()
				.map(x->x.getResource())
				.collect(Collectors.toSet());
		
		Map<ResourceInstance, Set<User>> usersPerResources =
		allocatedResources.
		stream().
		collect(
				Collectors.toMap(Function.identity(), 
				x-> {
					SortedSet<User>treeSet = new TreeSet<>((z,y)->z.toString().compareTo(y.toString()));
					treeSet.addAll(
							sortedList.stream()
							.filter(y-> y.getResource().equals(x))
							.map(y -> y.getUser())
							.collect(Collectors.toSet()));
					return treeSet;
				}));
	
		SortedSet<ResourceInstance> setOfResources = new TreeSet<ResourceInstance>(
				(x,y)->
				x.toString().compareTo(y.toString()));
		setOfResources.addAll(usersPerResources.keySet());
		for(ResourceInstance s: setOfResources)
		{
			Map<User,Integer> satisfactionPerUser = 
					usersPerResources.get(s)
					.stream()
					.collect(
							Collectors.toMap(
									Function.identity(),
									x->inF.
									getAllocationsPerResourceInstance().get(
											UserResourceInstanceAllocation.newInstance(x,s)))
							);
			
			Set<UserGroup>involvedGroups = 
					satisfactionPerUser.keySet().stream()
					.map(x->inF.getUserGroupOf(x))
					.collect(Collectors.toSet());
	
			Map<UserGroup, Integer> satisfactionPerGroup = 
					involvedGroups.stream()
					.collect(
							Collectors.toMap(Function.identity(), 
									x->
							satisfactionPerUser.get(x.getUsers().iterator().next())));
	
			System.out.println(s+"->"+satisfactionPerGroup+
					" "+inF.getOwner(s));
		}
	
		/*System.out.println("Allocation per group:");
		for(UserGroup ug: inF.getUserGroups())
			{
			System.out.println(
					res.stream()
					.filter(x-> ug.getUsers().contains(x))
					.collect(Collectors.toSet())
					);
			}*/
	}

	public static void displayInstanceStats(ProblemInstance inF) {
		System.out.println("Statistics for the current instance");
		System.out.println("Cumulative ranking per resource:"
				+ "(the kth value is for resource r is the number of users that "
				+ "rank r with value k or below)");
		
		System.out.println("Resources with high values for lower index are on high demand and thus likely to cause regret");
		
		Map<ResourceType, List<Long>> rankPerProject = 
				inF.getResourceTypes()
				.stream()
				.collect(
						Collectors.toMap(x->x, 
								x->new LinkedList()));
		
		
	
		for(int i = 0 ; i < inF.getResourceTypes().size();i++)
			for(ResourceType resource:inF.getResourceTypes())
			{
				final int temp = i;
				long count = inF.getAllocationsPerResourceType()
						.keySet()
						.stream()
						.filter(x->x.getResource().equals(resource))
						.filter(x->inF.getAllocationsPerResourceType().get(x)<=temp)
						.count();
				
			/*	System.out.println(exhaustivePrefsPerAllocation
				.keySet()
				.stream()
				.filter(x->x.resource.equals(resource))
				.filter(x->exhaustivePrefsPerAllocation.get(x)<=temp)
				.collect(Collectors.toSet()));*/
				rankPerProject.get(resource).add(count);
			}
		
		List<ResourceType> sortedResourceList = new LinkedList<>();
		sortedResourceList.addAll(inF.getResourceTypes());
		Collections.sort(sortedResourceList, (x,y)->x.toString().compareTo(y.toString()));
	
		
		System.out.println();
	
		System.out.print("R\t");
		for(int i = 0 ; i < sortedResourceList.size(); i++)
		System.out.print(i+"\t");
		
		System.out.println();
		
		 int maxNameLength = inF.getAllResourceOwners()
		 .stream()
		 .map(x->x.getName().length())
		 .max(Integer::max).get();
		
		for(ResourceType resource: sortedResourceList)
		{
			System.out.println(resource+"x"+inF.getAmountOf(resource)+"("+
		inF.getOwner(resource)+")\t"+
		rankPerProject.get(resource).toString()
		.replaceAll("\\[", "")
		.replaceAll("\\]", "")
		.replaceAll(",", "\t"));
		}
	}

}
