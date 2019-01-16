package solver;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import input.InputFormat;
import main.Main;
import model.Resource;
import model.ResourceProvider;
import model.User;
import model.UserAllocation;
import model.UserGroup;

public class Solver {
	
	public enum ConstraintsOnResourceOwners{
		NO_CONSTRAINTS,	MINIMIZE_RESOURCE_CONSUMMED_PER_OWNER;}


	public static Optional<Set<UserAllocation>> 
	optimizeAccordingToMaxInsatisfaction(
			int maxInsatisfaction,
			InputFormat inF,
			int maxNbResourcePerOwner)
	{
	
		try {
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);
			
			Set<UserAllocation> allAdmissibleAllocations = 
					inF.getExhaustiveAllocations()
					.keySet().stream()
					.filter(
							x->inF.getExhaustiveAllocations().get(x)<=maxInsatisfaction)
					.collect(Collectors.toSet());
	
			SortedSet<User> users = 
					new TreeSet<>((x,y)->x.toString().compareTo(y.toString()));
			users.addAll(inF.getAllUsers());
	
			SortedSet<Resource> allAdmissibleResources = new TreeSet<>((x,y)->x.toString().compareTo(y.toString()));
			
			allAdmissibleResources.addAll(
					allAdmissibleAllocations
					.stream().map(x->x.getResource()).collect(Collectors.toSet()));
			
			Map<UserAllocation, IloIntVar> allocToVar = 
					generateVariablePerAllocation(cplex,
					allAdmissibleAllocations
					);
			
			
			Map<Resource, IloIntVar> resourceMinLiftingJokerVars = 
					allAdmissibleResources.stream().collect(
							Collectors.toMap(Function.identity(),
									x->
									{
										try {
											return cplex
													.boolVar("jokerForDiscardingTheMinAllocationConstraint("+x+")"
													);
										} catch (IloException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
											throw new Error();
										}
									}));
	
			for(User pl:users)
			{
				int worseAllocValue = 1;
				if(allAdmissibleAllocations.stream()
						.anyMatch(x-> x.getUser().equals(pl)))
				{
					UserAllocation worseAlloc = allAdmissibleAllocations.stream()
							.filter(x-> x.getUser().equals(pl))
							.max((x,y)->inF.getExhaustiveAllocations().get(x)-
									inF.getExhaustiveAllocations().get(y)).get();
					worseAllocValue = inF.getExhaustiveAllocations().get(worseAlloc);
				}
	
				for(UserAllocation a: allAdmissibleAllocations)
					if(!allAdmissibleAllocations.contains(a) && a.getUser().equals(pl))
						inF.getExhaustiveAllocations().put(a, worseAllocValue+1);
			}
			
			Map<Resource, IloIntVar> varPerResource = new HashMap<>();
			for(Resource r: allAdmissibleResources)
			{
				try {
					varPerResource.put(r, cplex
							.boolVar("ActiveResourceVar("+r+")"
							));
				} catch (IloException e) {e.printStackTrace();throw new Error();}
			}
			
			
			
	
			
			cplex.addMinimize(
					Main.getExpressionToMinimize(
							cplex, 
							allAdmissibleAllocations,
							users,
							inF.getExhaustiveAllocations(),
							allocToVar));
			
			generateAllocationConstraints(
					cplex,
					allAdmissibleAllocations,
					allAdmissibleResources,
					allocToVar,
					varPerResource,
					resourceMinLiftingJokerVars,
					inF, maxNbResourcePerOwner
					);
			
			generateHardAllocationsConstraints(cplex, allocToVar, inF.getHardConstraints());
	
			cplex.exportModel("mipex1.lp");
	
			if (! cplex.solve() )
				return Optional.empty();
			System.out.println("Solution status = " + cplex.getStatus());
			Set<UserAllocation>s=
					processCplexResults(cplex, allocToVar);
			
			
	
			//s.sort((x,y)->roles.indexOf(x.role) - roles.indexOf(y.role));
			System.out.println("Minimizing the number of least happy people:");
			cplex.end();
			
			return Optional.of(s);
	
		}
		catch (IloException e) {
			System.err.println("Concert exception caught '" + e + "' caught");
			throw new Error();
		}
	}
	

	private static void addConstraintsOnResourceOwners(
			IloCplex cplex, 
			SortedSet<Resource> allAdmissibleResources, 
			Set<UserAllocation> allAdmissibleAllocations,
			InputFormat inF, 
			int maxNbResourcePerOwner, 
			Map<UserAllocation, IloIntVar> varPerAlloc,
			Map<Resource,IloIntVar> varPerResource) throws IloException {

		for(ResourceProvider rp : inF.getAllResourceOwners())
		{
			IloNumExpr countResourcesAllocatedForRP = cplex.constant(0);

			for(Resource r: inF.getResourcesFrom(rp)
					.stream()
					.filter(x->allAdmissibleResources.contains(x))
					.collect(Collectors.toSet()))
				countResourcesAllocatedForRP = cplex.sum(
						countResourcesAllocatedForRP, varPerResource.get(r));
			cplex.addGe(
					maxNbResourcePerOwner, 
					countResourcesAllocatedForRP, 
					"AllocateResourceFromAllocatorAtMost"+maxNbResourcePerOwner+"Times("
							+rp+","+inF.getMinNumUsersPerResource()+")");
		}
	}
	


	
	

	public static Set<UserAllocation> optimize(InputFormat input) {
		
		Set<UserAllocation> optimal = optimalAllocation(
				input);
		
		Main.printOutput(optimal, input.getExhaustiveAllocations());
		Main.processResults(optimal, input);			
		
		SatisfactionMeasure sm = SatisfactionMeasure.newInstance(optimal, input);
		
		optimalAllocation(
				input,
				sm);
		
		return optimal;
	
	}

	private static Set<UserAllocation> optimalAllocation(InputFormat input)
	{
		Optional<Set<UserAllocation>> res = Optional.empty();

		for(int i = 1 ; i <= input.getAllUsers().size()&&!res.isPresent(); i++)
		{
			System.out.println(
					"Trying to find an allocation with a maximum "
							+ "least satisfaction of rank:"+i);

			res= Solver.optimizeAccordingToMaxInsatisfaction(
					i,
					input,Integer.MAX_VALUE);
		}

		return res.get();
	}


	private static Set<UserAllocation> optimalAllocation(InputFormat input,
			SatisfactionMeasure smToReach) {
		Optional<Set<UserAllocation>> res = Optional.empty();
		
		int worseInsatisfactionToMeet = smToReach.getWorseAllocationValue();
			
			
		
		for(int i = 1 ; i <= input.getAllUsers().size()&&!res.isPresent(); i++)
		{
			System.out.println(
					"Trying to find an allocation with a maximum "
					+ "least satisfaction of rank:"+worseInsatisfactionToMeet+
					" and a maximum number of allocations per owner of:"
					+i
					);
			
			res= Solver.optimizeAccordingToMaxInsatisfaction(
					worseInsatisfactionToMeet,
					input,i);
			
			if(res.isPresent())
				if(smToReach.equals(SatisfactionMeasure.newInstance(res.get(),input)))
					return res.get();
				else 
				{
					Main.printOutput(res.get(), input.getExhaustiveAllocations());
					Main.processResults(res.get(), input);			
				}
				res = Optional.empty();
		}
		
		return res.get();
	}



	static void generateHardAllocationsConstraints(
			IloCplex cplex, 
			Map<UserAllocation, IloIntVar> varPerAlloc,
			Set<UserAllocation> hardConstraints) throws IloException {
		for(UserAllocation hc: hardConstraints)
			cplex.addEq(1,
					varPerAlloc.get(hc), "HardConstraint("+hc+")");
	}



	static Map<UserAllocation, IloIntVar> generateVariablePerAllocation(
			IloCplex cplex,
			Collection<UserAllocation> consideredAllocations
			) throws IloException {
		Map<UserAllocation, IloIntVar> varPerAlloc = new HashMap<>();
		
		for(UserAllocation pl : consideredAllocations)
				varPerAlloc.put(pl,
						cplex.boolVar(
						//cplex.numVar(0, 1,
								pl.toString()));
		
		return varPerAlloc;
	}



	static Set<UserAllocation> processCplexResults(IloCplex cplex, 
			Map<UserAllocation, IloIntVar> varPerAlloc) {
		return varPerAlloc.keySet().stream()
		.filter(x->{
			try {
				return cplex.getValue(varPerAlloc.get(x))>0.01;
			} catch (IloException e) {
				e.printStackTrace();
				throw new Error();
	
			}
		})
		.collect(Collectors.toSet());
	}
	
	public static void generateAllocationConstraints(
			IloCplex cplex, 
			Set<UserAllocation> allAdmissibleAllocations, 
			SortedSet<Resource> allAdmissibleResources,
			Map<UserAllocation, IloIntVar> varPerAlloc,
			Map<Resource, IloIntVar> allocatedResourceVar,
			Map<Resource, IloIntVar> allocationJoker,
			InputFormat inF,
			int maxNbResourcePerOwner
			)
					throws IloException{
		
		connectResourcesAndAllocations(cplex,
				inF, 
				allocatedResourceVar,
				varPerAlloc
				);
		
		Set<UserAllocation>validAllocations = varPerAlloc.keySet();
		for(User pl: inF.getAllUsers())
		{
			IloNumExpr oneResourcePerUser = 
					cplex.constant(0);
			for(UserAllocation al: inF.getAllAllocationsFor(pl))
			{
				if(!validAllocations.contains(al))continue;
				oneResourcePerUser = 
						cplex.sum(
						oneResourcePerUser,
						varPerAlloc.get(al));
			}
			cplex.addEq(
					1,
					oneResourcePerUser,
					"EachUserIsGivenExactlyOneResource("+pl+")");
		}

		for(Resource resource: allAdmissibleResources)
		{
			IloNumExpr maxKUsersPerResource = cplex.constant(0);
			for(UserAllocation ua: inF.getAllocationsForResource(resource)
					.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				
				maxKUsersPerResource = cplex.sum(
						maxKUsersPerResource, varPerAlloc.get(ua));
			}
			cplex.addGe(
					inF.getMaxNbUsersPerResource(), 
					maxKUsersPerResource, 
					"EachResourceIsAllocatedAtMostKTimes("+resource+","+
					inF.getMaxNbUsersPerResource()+")");
		}
		
		for(UserAllocation a:allAdmissibleAllocations)
		{
			cplex.addGe(
					allocatedResourceVar.get(a.getResource()),
					varPerAlloc.get(a),
					"CountsAsAllocatedIfAllocatedFor("+a.getResource()+","+a+")");
		}	
		
		for(Resource resource: allAdmissibleResources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation s: inF.getAllocationsForResource(resource)
					.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addLe(
					countUsersPerResource,
					0, 
					"EachNonAllocatedResourceIsAllocatedAtMost0TimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		for(Resource resource: allAdmissibleResources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation ua: inF.getAllocationsForResource(resource).stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(ua));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(inF.getMinNumUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addGe(
					countUsersPerResource,
					0, 
					"EachAllocatedResourceMustBeAllocatedAtLeastKTimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		
		IloNumExpr allJokers = cplex.constant(0);
		for(IloIntVar joker: allocationJoker.values())
			allJokers = cplex.sum(allJokers, joker);
		cplex.addLe(allJokers, 1,
				"AtMostOneJoker");
		
		for(Resource resource: allAdmissibleResources.stream()
				.filter(x->allAdmissibleAllocations.contains(x))
				.collect(Collectors.toSet()))
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation s: inF.getAllocationsForResource(resource))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
					
			cplex.addGe(
					0, 
					countUsersPerResource, 
					"EachAllocatedResourceIsAllocatedAtLeastKTimes("
					+resource+","+inF.getMinNumUsersPerResource()+")");
		}
		
		


		//ensure pairs
		for(UserGroup userGroup: inF.getUserGroups())
			for(Resource resource: allAdmissibleResources.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				User u0 = userGroup.getUsers().iterator().next();
				UserAllocation u0PicksR= UserAllocation.newInstance(u0, resource);
				
				if(!varPerAlloc.containsKey(u0PicksR)) continue;

				for(User u1: userGroup.getUsers())	
				{
					UserAllocation u1PicksR= UserAllocation.newInstance(u1, resource);

					IloNumExpr exprUser0PicksR =
							varPerAlloc.get(u0PicksR);

					IloNumExpr exprUser1PicksR =
							varPerAlloc.get(u1PicksR);

					cplex.addEq(exprUser0PicksR, 
							exprUser1PicksR,
							"PairedUsersMatchTheirDecisions("
									+u0+","
									+u1+","+
									resource+")");
				}
			}
		
		addConstraintsOnResourceOwners(
				cplex,
				allAdmissibleResources,
				allAdmissibleAllocations,
				inF,
				maxNbResourcePerOwner, varPerAlloc, allocatedResourceVar);
		
		
		//ensure adequate resource consumption
		/*for(String resource:resources)
		{
			IloIntVar usingRKTimes = cplex.boolVar();
			IloNumExpr total = cplex.prod(usingRKTimes, inF.numberOfUsersPerResource);
			for(Allocation a: 
				varPerAlloc.keySet().stream()
				.filter(x->x.resource.equals(resource))
				.collect(Collectors.toSet()))
			{
				total = cplex.sum(total, varPerAlloc.get(a));
			}
			cplex.addEq(total, inF.numberOfUsersPerResource, "FullAllocationOfResource("+resource+")");
		}*/
		
		
	}


	private static void connectResourcesAndAllocations(IloCplex cplex, InputFormat inF,
			Map<Resource, IloIntVar> allocatedResourceVar, 
			Map<UserAllocation, IloIntVar> varPerAlloc) throws IloException 
	{
		for(Resource r: allocatedResourceVar.keySet())
		{
			IloNumExpr allAllocations = cplex.constant(0);
			
			Set<UserAllocation> allocationsForResource = varPerAlloc.keySet()
					.stream()
					.filter(x->x.getResource().equals(r))
					.collect(Collectors.toSet());
					
			for(UserAllocation ua:allocationsForResource)
				allAllocations = cplex.sum(allAllocations, varPerAlloc.get(ua));
			
			IloNumExpr resource = 
					cplex.prod(allocationsForResource.size(), allocatedResourceVar.get(r));
			
			cplex.addLe(allAllocations, resource,
					"AllocationsMakeResourceConsummed("+r+")");
		}
	}


}
