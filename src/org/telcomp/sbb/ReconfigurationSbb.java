package org.telcomp.sbb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.slee.ActivityContextInterface;
import javax.slee.Address;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import org.telcomp.events.EndReconfigurationEvent;
import org.telcomp.events.StartReconfigurationEvent;

import servicecategory.Input;
import servicecategory.Operation;
import servicecategory.Output;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.mongodb.Mongo;

import contextinfo.User;

import datamodel.Arc;
import datamodel.PetriNet;
import datamodel.Place;
import datamodel.Token;
import datamodel.Transition;

public abstract class ReconfigurationSbb implements javax.slee.Sbb {

	private Datastore petriNets;
	private Datastore operationsRep;
	private String serviceName;
	private String operationName;
	private String userId;
	private Place reconfigInputPlace;
	private Place reconfigOutputPlace;
	private PetriNet retrievedPN;
	private HashMap<String, Object> reconfigurationInputs;
	
	public void onStartReconfigurationEvent(StartReconfigurationEvent event, ActivityContextInterface aci){
		long l = System.currentTimeMillis();
		
		System.out.println("-------------Reconfiguration Inputs----------");
		for(Entry<String, Object> entry : event.getReconfigInputs().entrySet()) {
			System.out.println(entry.getKey() + ": "+entry.getValue());
		}
		System.out.println("-------------Reconfiguration Inputs----------");
		System.out.println(" ");
		
		//Setting global Reconfiguration parameters
		reconfigurationInputs = event.getReconfigInputs();
		serviceName = (String) reconfigurationInputs.get("serviceName");
		userId = (String) reconfigurationInputs.get("userid");
		ArrayList<Place> IOPlaces = new ArrayList<Place>();
		Operation reconfigOperation;
		List<Operation> candidateOperations = new ArrayList<Operation>();
		
		//Retrieving Corresponding Petri Net from MongoDB
		try {
			Mongo mongo = new Mongo("localhost");
			petriNets = new Morphia().createDatastore(mongo, "PetriNetsManager");
			retrievedPN = petriNets.get(PetriNet.class, reconfigurationInputs.get("serviceName"));
			System.out.println("Retreived Petri Net Name: "+retrievedPN.getName());
			
			//Getting places from the service to be reconfigured
			for (Place p : retrievedPN.getPlaces()){
				if(p.getMainControlFlow() == Integer.parseInt((String) reconfigurationInputs.get("mainControlFlow"))){
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
			boolean reconfigurationCheck = false;
			for(Operation op : candidateOperations){
				//Discarding reconfigurated Operation as a candidate
				if(op.getId() != reconfigOperation.getId() && !(op.getOperationName().indexOf("Telco") >= 0)){
					System.out.println("Candidate Operation retrieved for Repository: "+op.getOperationName());
					//Context analysis
					Datastore users = new Morphia().createDatastore(mongo, "ContextManager");
					boolean contextCheck = false;
					if(reconfigOperation.getCategory().equals("messaging")){
						//Presence checking
						String contextUser = null;
						for(Entry<String, Object> entry : reconfigurationInputs.entrySet()) {
							String value = (String) entry.getValue();
							if(value.indexOf(reconfigInputPlace.getName()) >= 0){
								contextUser = value.substring(value.indexOf("-") + 1);
							}
						}
						User u = users.get(User.class, contextUser);
						System.out.println("User retreived: " + u.getId() + " Presence: " + u.getPresence());
						if(checkPresence(op, u)){
							contextCheck = true;
						}
					} else{
						//Location checking
						User u = users.get(User.class, userId);
						System.out.println("User retreived: " + u.getId() + " Location: " + u.getLocation());
						if(checkLocation(op, u)){
							contextCheck = true;
						}
					}
					System.out.println("Context analysis result: "+contextCheck);
					
					if(contextCheck){
						//Outputs Analysis
						Place candidateOutPl = new Place();
						candidateOutPl = outputAnalysis(reconfigOperation, op, candidateOutPl);
						if(candidateOutPl != null){
							//Inputs Analysis
							Place candidateInPl = new Place();
							candidateInPl = inputAnalysis(op, retrievedPN.getPlaces(), candidateInPl);
							if(candidateInPl != null){
								System.out.println("******************RESULT*******************");
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
								System.out.println("******************RESULT*******************");
					            System.out.println(" ");
					            //Update Petri Net and save it into DB
					            updatePetriNet(candidateInPl, candidateOutPl);
					            //Update Petri Net and modify Converged Service SBB class!!!!!!!
								EndReconfigurationEvent endEvent = new EndReconfigurationEvent(true);
								this.fireEndReconfigurationEvent(endEvent, aci, null);
								reconfigurationCheck = true;
								break;
							} else{
								System.out.println("Inputs not satisfied");
								System.out.println(" ");
							}
						} else{
							System.out.println("Outputs not satisfied");
			                System.out.println(" ");
						}
					} else{
						System.out.println("Context conditions were not satisfied");
						System.out.println(" ");
					}
				}
			}
			
			if(!reconfigurationCheck){
				//Reconfiguration failed
				EndReconfigurationEvent endEvent = new EndReconfigurationEvent(false);
				this.fireEndReconfigurationEvent(endEvent, aci, null);
			}
			mongo.close();
			System.out.println("Algorithm used time: " + (System.currentTimeMillis() - l) + "ms");
			aci.detach(this.sbbContext.getSbbLocalObject());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean compareBranchId(Place p){
		if(Integer.parseInt((String) reconfigurationInputs.get("branchControlFlow"+Integer.toString(p.getBranchId()))) == p.getBranchControlFlow() 
				&& p.getName().indexOf((String) reconfigurationInputs.get("operationName")) >= 0){
			return true;
		} else{
			return false;
		}
	}
	
	private boolean checkPresence(Operation op, User u){
		if(op.getPresenceInfo().equals(u.getPresence()) || op.getPresenceInfo().equals("any")){
			return true;
		} else {
			return false;
		}
	}
	
	private boolean checkLocation(Operation op, User u){
		if(op.getLocationInfo().equals(u.getLocation()) || op.getLocationInfo().equals("any")){
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
		
		System.out.println("*******************OUTPUTS CHECK**********************");
		if(!checkIO(outputsCheck, true)){candidateOutPl = null;}
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
				if(checkIO(inputsCheck, false)){break;}
			}
		} else{
			candidateInPl = null;
		}
		
		//Verifying if a free GetDataAccessTelcoService could satisfy an unsatisfied input
		if(!checkIO(inputsCheck, false)){
			for(Entry<String, String> entry : inputsCheck.entrySet()) {
				if(entry.getValue().equals("false")){
					Input ddi = couldBeFromGetDataAccess(entry.getKey(), candidateOp);
					Token dt = verifyPreviousDataAccess(candidateInPl);
					if(entry.getValue().equals("false") && ddi != null && dt != null){
						Token ntkn = new Token(candidateInPl.getName()+"_InputToken"+candidateInPl.getTokens().size(), 
								"input", ddi.getDataType(), dt.getDestiny(), ddi.getInputName());
						candidateInPl.getTokens().add(ntkn);
						inputsCheck.put(entry.getKey(), "true");
					}
				}
			}
		}
		
		System.out.println("*******************INTPUTS CHECK**********************");
		if(!checkIO(inputsCheck, true)){candidateInPl = null;}
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
	
	private boolean checkIO(HashMap<String, String> ioCheck, boolean print){
		boolean flag = true;
		for(Entry<String, String> entry : ioCheck.entrySet()) {
			if(print){
				System.out.println(("Parameter " + entry.getKey() + " satisfied? " + entry.getValue()));
			}
			if (entry.getValue().equals("false")) {
                flag = false;
            }
		}
		return flag;
	}
	
	private Input couldBeFromGetDataAccess(String inputName, Operation candidateOp){
		Input ri = null;
		
		for(Input i : candidateOp.getInputs()){
			if(i.getInputName().equals(inputName)){
				switch(i.getSubType()){
					case "email": {
						ri = i; 
						break;
					}
					case "linkedinid": {
						ri = i; 
						break;
					}
					case "twitterid": {
						ri = i; 
						break;
					}
					case "facebookid": {
						ri = i; 
						break;
					}
					case "caller": {
						ri = i; 
						break;
					}
					case "callee": {
						ri = i; 
						break;
					}
				}
				break;
			}
		}
		return ri;
	}
	
	private Token verifyPreviousDataAccess(Place candidateInPl){
		Token rt = null;
		
		main: for(Token t : reconfigInputPlace.getTokens()){
			for(Place p : retrievedPN.getPlaces()){
				if(p.getIdentifier().indexOf("OutputPlace") >= 0 && p.getName().indexOf("GetDataTelcoService") >= 0){
					for(Token t1 : p.getTokens()){
						if(t1.getDestiny().equals(t.getSource()) && verifyFreeDA(t1.getDestiny(), candidateInPl)){
							rt = t1;		
							break main;
						}
					}
				}
			}
		}
		return rt;
	}
	
	private boolean verifyFreeDA(String destiny, Place candidateInPl){
		boolean flag = true;
		
		for(Token t : candidateInPl.getTokens()){
			if(t.getSource().equals(destiny)){
				flag = false;
			}
		}
		return flag;
	}
	
	private void updatePetriNet(Place candidateInPl, Place candidateOutPl){
		petriNets.save(candidateInPl);
		petriNets.save(candidateOutPl);
		//Replacing Places from new service in Converged Service Petri Net
		replacePlace(candidateInPl, reconfigInputPlace.getIdentifier());
		replacePlace(candidateOutPl, reconfigOutputPlace.getIdentifier());
		//Adding new service transition
		Transition t = replaceTransition(candidateInPl.getName());
		//Update related Arcs
		updateArcs(candidateInPl, t, candidateOutPl, reconfigInputPlace.getName(), candidateInPl.getName());
		//Save updated Petri Net into DB
		petriNets.save(retrievedPN);
	}
	
	private void replacePlace(Place p, String reconfigPlaceId){
		for(int i=0; i<retrievedPN.getPlaces().size(); i++){
			if(retrievedPN.getPlaces().get(i).getIdentifier().equals(reconfigPlaceId)){
				petriNets.delete(retrievedPN.getPlaces().get(i));
				retrievedPN.getPlaces().remove(i);
				retrievedPN.getPlaces().add(i, p);
			}
		}
	}
	
	private Transition replaceTransition(String opName){
		Transition t = new Transition(retrievedPN.getName() + "_" + opName + "_Transition", opName);
		petriNets.save(t);
		for(int i=0; i<retrievedPN.getTransitions().size(); i++){
			if(retrievedPN.getTransitions().get(i).getName().equals(reconfigInputPlace.getName())){
				petriNets.delete(retrievedPN.getTransitions().get(i));
				retrievedPN.getTransitions().remove(i);
				retrievedPN.getTransitions().add(i, t);
			}
		}
		return t;
	}
	
	private void updateArcs(Place inPlace, Transition t, Place outPlace, String reconfigName, String opName){
		for(int i=0; i<retrievedPN.getArcs().size(); i++){
			if(retrievedPN.getArcs().get(i).getOutputPlace() != null && retrievedPN.getArcs().get(i).getInputTransition() != null &&
					retrievedPN.getArcs().get(i).getOutputPlace().getName().equals(reconfigName) && 
					retrievedPN.getArcs().get(i).getInputTransition().getName().indexOf(reconfigName) < 0){
				retrievedPN.getArcs().get(i).setOutputPlace(inPlace);
				petriNets.save(retrievedPN.getArcs().get(i));
			}
			if(retrievedPN.getArcs().get(i).getInputPlace() != null && retrievedPN.getArcs().get(i).getOutputTransition() != null &&
					retrievedPN.getArcs().get(i).getInputPlace().getName().equals(reconfigName) && 
					retrievedPN.getArcs().get(i).getOutputTransition().getName().equals(reconfigName)){
				Arc a = new Arc(retrievedPN.getName() + "_" + opName + "_InputArc", opName, inPlace, t);
				petriNets.delete(retrievedPN.getArcs().get(i));
				retrievedPN.getArcs().remove(i);
				retrievedPN.getArcs().add(i, a);
				petriNets.save(a);
			}
			if(retrievedPN.getArcs().get(i).getOutputPlace() != null && retrievedPN.getArcs().get(i).getInputTransition() != null &&
					retrievedPN.getArcs().get(i).getOutputPlace().getName().equals(reconfigName) && 
					retrievedPN.getArcs().get(i).getInputTransition().getName().equals(reconfigName)){
				Arc a = new Arc(retrievedPN.getName() + "_" + opName + "_OutputArc", opName, t, outPlace);
				petriNets.delete(retrievedPN.getArcs().get(i));
				retrievedPN.getArcs().remove(i);
				retrievedPN.getArcs().add(i, a);
				petriNets.save(a);
			}
			if(retrievedPN.getArcs().get(i).getInputPlace() != null && retrievedPN.getArcs().get(i).getOutputTransition() != null &&
					retrievedPN.getArcs().get(i).getInputPlace().getName().equals(reconfigName) 
					&& retrievedPN.getArcs().get(i).getOutputTransition().getName().indexOf(reconfigName) < 0){
				retrievedPN.getArcs().get(i).setInputPlace(outPlace);
				petriNets.save(retrievedPN.getArcs().get(i));
			}
		}
	}
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) {this.sbbContext = context;}
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
    
    public abstract void fireEndReconfigurationEvent (EndReconfigurationEvent event, ActivityContextInterface aci, Address address);
	

	
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
