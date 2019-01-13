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
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import input.InputFormat;
import main.Main;
import model.Resource;
import model.User;
import model.UserAllocation;

public class Solver {

	public static Optional<Set<UserAllocation>> 
	optimizeAccordingToMaxInsatisfaction(
			int maxInsatisfaction,
			InputFormat inF)
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
	
			SortedSet<Resource> resources = new TreeSet<>((x,y)->x.toString().compareTo(y.toString()));
			
			resources.addAll(
					allAdmissibleAllocations.stream().map(x->x.getResource()).collect(Collectors.toSet()));
			
			Map<UserAllocation, IloNumVar> allocToVar = 
					generateVariablePerAllocation(cplex,
					allAdmissibleAllocations
					);
			
			Map<Resource, IloIntVar> isAllocatedResourceVars = 
					resources.stream().collect(
							Collectors.toMap(Function.identity(),
									x->
									{
										try {
											return cplex.boolVar("isAllocated("+x+")"
													);
										} catch (IloException e) {
											e.printStackTrace();
											throw new Error();
										}
									}));
			
			Map<Resource, IloIntVar> resourceMinLiftingJokerVars = 
					resources.stream().collect(
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
			
	
			
			cplex.addMinimize(
					Main.getExpressionToMinimize(
							cplex, 
							allAdmissibleAllocations,
							users,
							inF.getExhaustiveAllocations(),
							allocToVar));
			
			Main.generateAllocationConstraints(
					cplex,
					allAdmissibleAllocations,
					resources,
					allocToVar,
					isAllocatedResourceVars,
					resourceMinLiftingJokerVars,
					inF
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
			Main.printOutput(s,inF.getExhaustiveAllocations());
			cplex.end();
			
			return Optional.of(s);
	
		}
		catch (IloException e) {
			System.err.println("Concert exception caught '" + e + "' caught");
			throw new Error();
		}
	}
	


	public static Set<UserAllocation> optimize(InputFormat input) {
		Optional<Set<UserAllocation>> res = Optional.empty();
		for(int i = 1 ; i <= input.getAllUsers().size()&&!res.isPresent(); i++)
		{
			System.out.println(
					"Trying to find an allocation with a maximum "
					+ "least satisfaction of rank:"+i);
			
			res= Solver.optimizeAccordingToMaxInsatisfaction(
					i,
					input);
		}
		
		return res.get();
	}



	static void generateHardAllocationsConstraints(
			IloCplex cplex, 
			Map<UserAllocation, IloNumVar> varPerAlloc,
			Set<UserAllocation> hardConstraints) throws IloException {
		for(UserAllocation hc: hardConstraints)
			cplex.addEq(1,
					varPerAlloc.get(hc), "HardConstraint("+hc+")");
	}



	static Map<UserAllocation, IloNumVar> generateVariablePerAllocation(
			IloCplex cplex,
			Collection<UserAllocation> consideredAllocations
			) throws IloException {
		Map<UserAllocation, IloNumVar> varPerAlloc = new HashMap<>();
		
		for(UserAllocation pl : consideredAllocations)
				varPerAlloc.put(pl,
						cplex.boolVar(
						//cplex.numVar(0, 1,
								pl.toString()));
		
		return varPerAlloc;
	}



	static Set<UserAllocation> processCplexResults(IloCplex cplex, Map<UserAllocation, IloNumVar> varPerAlloc) {
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


}
