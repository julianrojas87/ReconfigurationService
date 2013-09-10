package org.telcomp.sbb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.serviceactivity.ServiceActivity;
import javax.slee.serviceactivity.ServiceActivityFactory;
import javax.slee.serviceactivity.ServiceStartedEvent;

import servicecategory.Input;
import servicecategory.Operation;
import servicecategory.Output;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.mongodb.Mongo;

import datamodel.PetriNet;
import datamodel.Place;
import datamodel.Token;

public abstract class ReconfigurationSbb implements javax.slee.Sbb {
	
	private ServiceActivityFactory saf;
	private Datastore petriNets;
	private Datastore operationsRep;
	private String serviceName;
	private String operationName;
	private Place reconfigInputPlace;
	private Place reconfigOutputPlace;
	private PetriNet retrievedPN;
	private HashMap<String, String> reconfigurationInputs;

	public void onServiceStartedEvent (ServiceStartedEvent  event, ActivityContextInterface aci) {
		ServiceActivity sa = saf.getActivity();
		if(sa.equals(aci.getActivity())){
			aci.detach(this.sbbContext.getSbbLocalObject());
			
			//Simulating Input obtained from Monitoring Service
			HashMap<String, String> reconfigInputs = new HashMap<String, String>();
			reconfigInputs.put("ServiceName", "LinkedInJobNotificator");
			reconfigInputs.put("operationName", "sendTwitterMessage");
			//reconfigInputs.put("operationName", "getLinkedInJobs");
			reconfigInputs.put("mainControlFlow", "4");
			reconfigInputs.put("branchControlFlow1", "2");
			reconfigInputs.put("branchControlFlow2", "2");
			
			//Setting global Reconfiguration parameters
			reconfigurationInputs = reconfigInputs;
			serviceName = reconfigurationInputs.get("ServiceName");
			ArrayList<Place> IOPlaces = new ArrayList<Place>();
			Operation reconfigOperation;
			List<Operation> candidateOperations = new ArrayList<Operation>();
			
			//Retrieving Corresponding Petri Net from MongoDB
			try {
				Mongo mongo = new Mongo("localhost");
				petriNets = new Morphia().createDatastore(mongo, "PetriNetsManager");
				retrievedPN = petriNets.get(PetriNet.class, reconfigurationInputs.get("ServiceName"));
				System.out.println("Retreived Petri Net Name: "+retrievedPN.getName());
				
				//Getting places from the service to be reconfigured
				for (Place p : retrievedPN.getPlaces()){
					if(p.getMainControlFlow() == Integer.parseInt(reconfigurationInputs.get("mainControlFlow"))){
						if(p.getBranchId() == 0){
							IOPlaces.add(p);
						} else{
							if(compareBranchId(p)){
								IOPlaces.add(p);
							}
						}
					}
				}
				
				System.out.println("*****************RETRIEVED PLACES**********************");
				for(Place p : IOPlaces){
					System.out.println("Retrieved Place Name: "+ p.getIdentifier());
					for(Token t : p.getTokens()){
						System.out.println("Parameter Type: "+t.getType()+" coming from: "+t.getSource()+" going to: "+t.getDestiny());
					}
				}
				System.out.println("*****************RETRIEVED PLACES**********************");
				
				//Storing I/O Places from service to be reconfigured individually
				if(IOPlaces.get(0).getIdentifier().indexOf("InputPlace") >= 0){
					reconfigInputPlace = IOPlaces.get(0);
					reconfigOutputPlace = IOPlaces.get(1);
				} else{
					reconfigOutputPlace = IOPlaces.get(0);
					reconfigInputPlace = IOPlaces.get(1);
				}
				
				//Retrieving Operation object from MongoDB Operations Repository
				operationsRep = new Morphia().createDatastore(mongo, "OperationsManager");
				operationName = IOPlaces.get(0).getName().substring(0, IOPlaces.get(0).getName().length() - 1);
				reconfigOperation = operationsRep.find(Operation.class).field("operationName").equal(operationName).get();
				System.out.println(" ");
				System.out.println(" ");
				System.out.println("Operation to reconfigure retrieved from repository: "+reconfigOperation.getOperationName());
				
				//Retrieving candidate operations to replace reconfigurated service from Repository
				candidateOperations = operationsRep.find(Operation.class).field("category").equal(reconfigOperation.getCategory()).asList();
				for(Operation op : candidateOperations){
					//Discarding reconfigurated Operation as a candidate
					if(op.getId() != reconfigOperation.getId()){
						System.out.println("Candidate Operation retrieved for Repository: "+op.getOperationName());
						//Outputs Analysis
						Place candidateOutPl = new Place();
						candidateOutPl = outputAnalysis(reconfigOperation, op, candidateOutPl);
						//Inputs Analysis
						Place candidateInPl = new Place();
						candidateInPl = inputAnalysis(op, retrievedPN.getPlaces(), candidateInPl);
						
						System.out.println("******************RESULT*******************");
						if(candidateOutPl != null){
							System.out.println("Candidate Output Place Id: "+candidateOutPl.getIdentifier());
							System.out.println("Candidate Output Place Name: "+candidateOutPl.getName());
							System.out.println("Candidate Output Place MainControlFlow: "+candidateOutPl.getMainControlFlow());
							System.out.println("Candidate Output Place BranchId: "+candidateOutPl.getBranchId());
							System.out.println("Candidate Output Place BranchControlFlow: "+candidateOutPl.getBranchControlFlow());
							for(Token t: candidateOutPl.getTokens()){
								System.out.println("Token Id: "+t.getIdentifier());
								System.out.println("Token Source: "+t.getSource());
								System.out.println("Token Destiny: "+t.getDestiny());
							}
							System.out.println(" ");
						} else{
							System.out.println("Outputs not satisfied");
							System.out.println(" ");
						}
						
						if(candidateInPl != null){
							System.out.println("Candidate Input Place Id: "+candidateInPl.getIdentifier());
							System.out.println("Candidate Input Place Name: "+candidateInPl.getName());
							System.out.println("Candidate Input Place MainControlFlow: "+candidateInPl.getMainControlFlow());
							System.out.println("Candidate Input Place BranchId: "+candidateInPl.getBranchId());
							System.out.println("Candidate Input Place BranchControlFlow: "+candidateInPl.getBranchControlFlow());
							for(Token t: candidateInPl.getTokens()){
								System.out.println("Token Id: "+t.getIdentifier());
								System.out.println("Token Source: "+t.getSource());
								System.out.println("Token Destiny: "+t.getDestiny());
							}
						} else{
							System.out.println("Inputs not satisfied");
						}
						System.out.println("******************RESULT*******************");
						System.out.println(" ");
					}
				}
				mongo.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean compareBranchId(Place p){
		if(Integer.parseInt(reconfigurationInputs.get("branchControlFlow"+Integer.toString(p.getBranchId()))) == p.getBranchControlFlow() 
				&& p.getName().indexOf(reconfigurationInputs.get("operationName")) >= 0){
			return true;
		} else{
			return false;
		}
	}
	
	private Place outputAnalysis(Operation reconfigOp, Operation candidateOp, Place candidateOutPl){
		HashMap<String, String> outputsCheck = new HashMap<String, String>();
		
		candidateOutPl.setName(candidateOp.getOperationName() + getOpId(candidateOp));
		candidateOutPl.setIdentifier(serviceName + "_" + candidateOutPl.getName() + "_OutputPlace");
		candidateOutPl.setMainControlFlow(reconfigOutputPlace.getMainControlFlow());
		candidateOutPl.setBranchId(reconfigOutputPlace.getBranchId());
		candidateOutPl.setBranchControlFlow(reconfigOutputPlace.getBranchControlFlow());
		
		for(Output rout : reconfigOp.getOutputs()){
			outputsCheck.put(rout.getOutputName(), "false");
			for(Output cout: candidateOp.getOutputs()){
				if(rout.getType().equals(cout.getType()) && (rout.getSubType().equals(cout.getSubType()) || 
						rout.getSubType().equals("any"))){
					Token t = getCandidateToken(cout, rout, candidateOutPl.getName(), outputsCheck.size()-1);
					candidateOutPl.getTokens().add(t);
					outputsCheck.put(rout.getOutputName(), "true");
				}
			}
		}
		
		Iterator<Entry<String, String>> it = outputsCheck.entrySet().iterator();
		
		System.out.println("*******************OUTPUTS CHECK**********************");
		while(it.hasNext()){
			Entry<String, String> e = (Entry<String, String>) it.next();
			System.out.println("Output: "+e.getKey()+" is satisfied? "+e.getValue());
			if(e.getValue().equals("false")){
				candidateOutPl = null;
			}
		}
		System.out.println("*******************OUTPUTS CHECK**********************");
		return candidateOutPl;
	}
	
	private Place inputAnalysis(Operation candidateOp, ArrayList<Place> places, Place candidateInPl){
		HashMap<String, String> inputsCheck = new HashMap<String, String>();
		
		candidateInPl.setName(candidateOp.getOperationName() + getOpId(candidateOp));
		candidateInPl.setIdentifier(serviceName + "_" + candidateInPl.getName() + "_InputPlace");
		candidateInPl.setMainControlFlow(reconfigInputPlace.getMainControlFlow());
		candidateInPl.setBranchId(reconfigInputPlace.getBranchId());
		candidateInPl.setBranchControlFlow(reconfigInputPlace.getBranchControlFlow());
		
		for(Input cin : candidateOp.getInputs()){
			inputsCheck.put(cin.getInputName(), "false");
		}
		
		if(inputsCheck.size() > 0){
			for(Place p : places){
				if((p.getMainControlFlow() < reconfigInputPlace.getMainControlFlow() || 
						(p.getBranchId() != 0 && p.getBranchId() == reconfigInputPlace.getBranchId())) && 
						(p.getIdentifier().indexOf("OutputPlace") >= 0 || p.getIdentifier().indexOf("StartPlace") >= 0) && 
						!p.getName().equals(reconfigInputPlace.getName())){
					for(Token t : p.getTokens()){
						Input i = getDestinyInput(t.getDestiny(), places);
						if(i != null){
							for(Input i0 : candidateOp.getInputs()){
								if(i0.getType().equals(i.getType()) && (i0.getSubType().equals(i.getSubType()) 
										|| i0.getSubType().equals("any"))){
									Token it = new Token(candidateInPl.getName()+"_InputToken"+candidateInPl.getTokens().size(), 
											"input", i0.getDataType(), t.getDestiny(), i0.getInputName());
									candidateInPl.getTokens().add(it);
									inputsCheck.put(i0.getInputName(), "true");
								}
							}
						}
					}
				}
				boolean flag = true;
				Iterator<Entry<String, String>> it = inputsCheck.entrySet().iterator();
				while(it.hasNext()){
					Entry<String, String> e = (Entry<String, String>) it.next();
					if(e.getValue().equals("false")){
						flag = false;
					}
				}
				if(flag){break;}
			}
		} else{
			candidateInPl = null;
		}
		
		System.out.println("*******************INTPUTS CHECK**********************");
		Iterator<Entry<String, String>> it = inputsCheck.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, String> e = (Entry<String, String>) it.next();
			System.out.println("Input: "+e.getKey()+" is satisfied? "+e.getValue());
			if(e.getValue().equals("false")){
				candidateInPl = null;
			}
		}
		System.out.println("*******************INTPUTS CHECK**********************");
		return candidateInPl;
	}
	
	private Input getDestinyInput(String destiny, ArrayList<Place> places){
		Input in = null;
		
		main: for(Place p : places){
			for(Token t : p.getTokens()){
				if(t.getType().equals("input") && t.getSource().equals(destiny)){
					Operation op = operationsRep.find(Operation.class).field("operationName")
							.equal(p.getName().substring(0, p.getName().length() - 1)).get();
					for(Input i : op.getInputs()){
						if(i.getInputName().equals(t.getDestiny())){
							in = i;
							break main;
						}
					}
				}
			}
		}
		return in;
	}
	
	private int getOpId(Operation op){
		int i = 0;
		
		for(Place p : retrievedPN.getPlaces()){
			if(p.getIdentifier().indexOf("InputPlace") >= 0 && p.getName().indexOf(op.getOperationName()) >= 0){
				i++;
			}
		}	
		return i;
	}
	
	private Token getCandidateToken(Output cout, Output rout, String candidatePlaceName, int size){
		Token rtoken = new Token(candidatePlaceName+"_OutputToken"+size, "output", cout.getDataType(), cout.getOutputName(), "");
		for(Token t : reconfigOutputPlace.getTokens()){
			if(t.getSource().equals(rout.getOutputName())){
				rtoken.setDestiny(t.getDestiny());
			}
		}
		return rtoken;
	}
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			saf = (ServiceActivityFactory) ctx.lookup("slee/serviceactivity/factory"); 
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
    public void unsetSbbContext() { this.sbbContext = null; }
    
    // TODO: Implement the lifecycle methods if required
    public void sbbCreate() throws javax.slee.CreateException {}
    public void sbbPostCreate() throws javax.slee.CreateException {}
    public void sbbActivate() {}
    public void sbbPassivate() {}
    public void sbbRemove() {}
    public void sbbLoad() {}
    public void sbbStore() {}
    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
    public void sbbRolledBack(RolledBackContext context) {}
	

	
	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this 
	 * method, the sbbContext variable and the variable assignment in setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
