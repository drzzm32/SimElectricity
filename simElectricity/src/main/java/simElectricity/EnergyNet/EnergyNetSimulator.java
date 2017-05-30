package simElectricity.EnergyNet;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import simElectricity.Common.ConfigManager;
import simElectricity.Common.SEUtils;
import simElectricity.EnergyNet.Matrix.IMatrixResolver;

import simElectricity.EnergyNet.Components.Cable;
import simElectricity.EnergyNet.Components.ConstantPowerLoad;
import simElectricity.EnergyNet.Components.DiodeInput;
import simElectricity.EnergyNet.Components.DiodeOutput;
import simElectricity.EnergyNet.Components.GridNode;
import simElectricity.EnergyNet.Components.RegulatorController;
import simElectricity.EnergyNet.Components.RegulatorInput;
import simElectricity.EnergyNet.Components.RegulatorOutput;
import simElectricity.EnergyNet.Components.SEComponent;
import simElectricity.EnergyNet.Components.TransformerPrimary;
import simElectricity.EnergyNet.Components.TransformerSecondary;
import simElectricity.EnergyNet.Components.VoltageSource;
import simElectricity.EnergyNet.Components.SwitchA;
import simElectricity.EnergyNet.Components.SwitchB;


public class EnergyNetSimulator{
	//Contains information about the grid
	protected EnergyNetDataProvider dataProvider;
	
    //Matrix solving algorithm used to solve the problem
	protected IMatrixResolver matrix;
    //Records the number of iterations during last iterating process
	protected int iterations;
    //The allowed mismatch
	protected double epsilon;
    
    
    //The conductance placed between each PN junction(to alleviate convergence problem)
	protected double Gpn;
    //Diode parameter for regulator controllers
	protected double Vt = 26e-6;
	protected double Is = 1e-6;
    
    private double[] calcCurrents(double[] voltages, List<SEComponent> unknownVoltageNodes){
    	int matrixSize = unknownVoltageNodes.size();
    	
    	double[] currents = new double[matrixSize];   
    	
    	//Calculate the current flow into each node using their voltage
        for (int nodeIndex = 0; nodeIndex < matrixSize; nodeIndex++) {
        	SEComponent curNode = unknownVoltageNodes.get(nodeIndex);
         	
        	//currents[nodeIndex] = -voltages[nodeIndex]*Gnode;
            
        	//Node - Node
			Iterator<SEComponent> iteratorON = curNode.optimizedNeighbors.iterator();
			Iterator<Double> iteratorR = curNode.optimizedResistance.iterator();
			while (iteratorON.hasNext()){
				SEComponent neighbor = iteratorON.next();
        		int iNeighbor = unknownVoltageNodes.indexOf(neighbor);
        		double R = iteratorR.next();
        		currents[nodeIndex] -= (voltages[nodeIndex] - voltages[iNeighbor])/R;					
			}			
			
			
			
			
			//Cable - GridNode interconnection
			if (curNode instanceof Cable){
				Cable cable = (Cable) curNode;
				
				if (cable.connectedGridNode != null && cable.isGridLinkEnabled)
					currents[nodeIndex] -= (voltages[nodeIndex]/cable.resistance) - (voltages[unknownVoltageNodes.indexOf(cable.connectedGridNode)]/cable.resistance);
			}else if (curNode instanceof GridNode){
				GridNode gridNode = (GridNode) curNode;
				
				if (gridNode.interConnection != null && gridNode.interConnection.isGridLinkEnabled)
					currents[nodeIndex] -= (voltages[nodeIndex]/gridNode.interConnection.resistance) - (voltages[unknownVoltageNodes.indexOf(gridNode.interConnection)]/gridNode.interConnection.resistance);	
			}
			
			//Node - shunt and two port networks
			else if (curNode instanceof VoltageSource){
    			VoltageSource vs = (VoltageSource) curNode;
    			currents[nodeIndex] -= (voltages[nodeIndex] - vs.v) / vs.r;
    		}else if (curNode instanceof ConstantPowerLoad){
    			ConstantPowerLoad load = (ConstantPowerLoad)curNode;
    			
    			double V = voltages[unknownVoltageNodes.indexOf(curNode)];
    			double Rcal = V*V/ load.pRated;
    			
    			if (Rcal > load.rMax)
    				Rcal = load.rMax;
    			if (Rcal < load.rMin)
    				Rcal = load.rMin;
    			
    			if (load.enabled)
    				currents[nodeIndex] -= V/Rcal;
    		}
    		
			//Switch
    		else if (curNode instanceof SwitchA){
    			SwitchA A = (SwitchA) curNode;
    			SwitchB B = A.B;
    			double resistance = A.resistance;
    			int iB = unknownVoltageNodes.indexOf(B);
    			
    			if (A.isOn)
    				currents[nodeIndex] -= (voltages[nodeIndex]/resistance) - (voltages[iB]/resistance);
    		}else if (curNode instanceof SwitchB){
    			SwitchB B = (SwitchB) curNode;
    			SwitchA A = B.A;
    			double resistance = A.resistance;
    			int iA = unknownVoltageNodes.indexOf(A);
    			
    			if (A.isOn)
    				currents[nodeIndex] -= -(voltages[iA]/resistance) + (voltages[nodeIndex]/resistance);
    		}
			
    		//Transformer
    		else if (curNode instanceof TransformerPrimary){
    			TransformerPrimary pri = (TransformerPrimary) curNode;
    			TransformerSecondary sec = pri.secondary;
    			double ratio = pri.ratio;
    			double res = pri.rsec;
    			int iSec = unknownVoltageNodes.indexOf(sec);
    			currents[nodeIndex] -= (voltages[nodeIndex]*ratio*ratio/res) - (voltages[iSec]*ratio/res);
    		}else if (curNode instanceof TransformerSecondary){
    			TransformerSecondary sec = (TransformerSecondary) curNode;
    			TransformerPrimary pri = sec.primary;
    			double ratio = pri.ratio;
    			double res = pri.rsec;
    			int iPri = unknownVoltageNodes.indexOf(pri);
    			currents[nodeIndex] -= -(voltages[iPri]*ratio/res) + (voltages[nodeIndex]/res);
    		}
    		
			//Regulator
    		else if (curNode instanceof RegulatorInput){
    			RegulatorInput input = (RegulatorInput) curNode;
    			RegulatorOutput output = input.output;
    			RegulatorController controller = input.controller;
    			
    			double Vi = voltages[nodeIndex];
    			double Vo = voltages[unknownVoltageNodes.indexOf(output)];	
    			double Vc = voltages[unknownVoltageNodes.indexOf(controller)];
    			double Ro = input.Ro;
    			double Dmax = input.Dmax;
    			
    			double Ii = Vi*(Vc+Dmax)*(Vc+Dmax)/Ro - Vo*(Vc+Dmax)/Ro;
				
				currents[nodeIndex] -= Ii;
    		}else if (curNode instanceof RegulatorOutput){
    			RegulatorOutput output = (RegulatorOutput) curNode;
    			RegulatorInput input = output.input;
    			RegulatorController controller = input.controller;
    			
    			double Vi = voltages[unknownVoltageNodes.indexOf(input)];
    			double Vo = voltages[nodeIndex];	
    			double Vc = voltages[unknownVoltageNodes.indexOf(controller)];
    			double Ro = input.Ro;
    			double Dmax = input.Dmax;
    			double Rdummy = input.Rdummy;
    			
    			double Io = -Vi*(Vc+Dmax)/Ro + Vo/Ro + Vo/Rdummy;
				
				currents[nodeIndex] -= Io;
    		}else if (curNode instanceof RegulatorController){
    			RegulatorController controller = (RegulatorController) curNode;
    			RegulatorInput input = controller.input;
    			RegulatorOutput output = input.output;
    			
    			double Vo = voltages[unknownVoltageNodes.indexOf(output)];	
    			double Vc = voltages[nodeIndex];
    			double A = input.A;
    			double Rs = input.Rs;
    			double Rc = input.Rc;
    			double Dmax = input.Dmax;
    			
    			
    			double Io = Vo*A/Rs + Vc/Rs + Dmax/Rs - input.Vref*A/Rs;
				
    			if (Vc > Vt*Math.log(Vt/Is/Rc))
    				Io += Vc/Rc;
    			else
    				Io += Is*Math.exp(Vc/Vt);
    			
				currents[nodeIndex] -= Io;
    		}
    		
    		
			//Diode
    		else if (curNode instanceof DiodeInput){
    			DiodeInput input = (DiodeInput) curNode;
    			DiodeOutput output = input.output;
    			int iPri = nodeIndex;
    			int iSec = unknownVoltageNodes.indexOf(output);	
    			
    			double Vd = voltages[iPri]-voltages[iSec];
    			double Vt = input.Vt;
    			double Is = input.Is;
    			double Id;
    			

    			double Rmin = input.Rs;
    			if (Vd>Vt*Math.log(Vt/Is/Rmin)){
    				Id = Vd/Rmin + Vd*Gpn;
    			}else{
    				Id = Is*Math.exp(Vd/Vt) + Vd*Gpn;
    			}
    			
    			currents[nodeIndex] -= Id;
    		}else if (curNode instanceof DiodeOutput){
    			DiodeOutput output = (DiodeOutput) curNode;
    			DiodeInput input = output.input;
    			int iPri = unknownVoltageNodes.indexOf(input);
    			int iSec = nodeIndex;
    			
    			double Vd = voltages[iPri]-voltages[iSec];
    			double Vt = input.Vt;
    			double Is = input.Is;
    			double Id;
    			
    			double Rmin = input.Rs;
    			if (Vd>Vt*Math.log(Vt/Is/Rmin)){
    				Id = Vd/Rmin + Vd*Gpn;
    			}else{
    				Id = Is*Math.exp(Vd/Vt) + Vd*Gpn;
    			}

    			
    			currents[nodeIndex] += Id;
    		}
        }
    	
    	return currents;
    }

    private void formJacobian(double[] voltages, List<SEComponent> unknownVoltageNodes){
    	int matrixSize = unknownVoltageNodes.size();
    	
    	matrix.newMatrix(matrixSize);

        for (int columnIndex = 0; columnIndex < matrixSize; columnIndex++) {
        	SEComponent columnNode = unknownVoltageNodes.get(columnIndex);
        
        	double diagonalElement = 0;
        	
        	//Add conductance between nodes
			Iterator<SEComponent> iteratorON = columnNode.optimizedNeighbors.iterator();
			Iterator<Double> iteratorR = columnNode.optimizedResistance.iterator();
			while (iteratorON.hasNext()){
				SEComponent neighbor = iteratorON.next();
				int rowIndex = unknownVoltageNodes.indexOf(neighbor);
        		double R = iteratorR.next();	
        		
        		diagonalElement += 1.0D / R;
        		
        		matrix.setElementValue(columnIndex, rowIndex, -1.0D / R);
			}
			
			
			
			
			//Cable - GridNode interconnection
			if (columnNode instanceof Cable){
				Cable cable = (Cable)columnNode;
				
				if (cable.connectedGridNode != null && cable.isGridLinkEnabled){
					int iCable = columnIndex;
					int iGridNode = unknownVoltageNodes.indexOf(cable.connectedGridNode);
					
					//Diagonal element
					diagonalElement += 1.0D/cable.resistance;	
					
	       			//Off-diagonal elements
	       			matrix.setElementValue(iCable, iGridNode, -1.0D / cable.resistance);
	       			matrix.setElementValue(iGridNode, iCable, -1.0D / cable.resistance);
				}
			}else if (columnNode instanceof GridNode){
				GridNode gridNode = (GridNode) columnNode;
				
				if (gridNode.interConnection != null && gridNode.interConnection.isGridLinkEnabled){
					diagonalElement += 1.0D / gridNode.interConnection.resistance;
				}
			}
			
        	
        	//Process voltage sources and resistive loads
			else if (columnNode instanceof VoltageSource){
				diagonalElement += 1.0D / ((VoltageSource) columnNode).r;   						
			}
        	
        	//Constant power load
			else if (columnNode instanceof ConstantPowerLoad){
				ConstantPowerLoad load = (ConstantPowerLoad)columnNode;
				double V = voltages[unknownVoltageNodes.indexOf(columnNode)];
				
    			double Rcal = V*V/load.pRated;
    			
    			if (Rcal > load.rMax)
    				Rcal = load.rMax;
    			if (Rcal < load.rMin)
    				Rcal = load.rMin;
    			
    			if (load.enabled)
    				diagonalElement += 1.0D / Rcal;
			}
        	
			//Two port networks
			//Switch
			else if (columnNode instanceof SwitchA){
				SwitchA A = (SwitchA) columnNode;
				
				if (A.isOn){
					int iA = columnIndex;
					int iB = unknownVoltageNodes.indexOf(A.B);
					
					//Diagonal element
					diagonalElement += 1.0D/A.resistance;	
					
	       			//Off-diagonal elements
	       			matrix.setElementValue(iA, iB, -1.0D / A.resistance);
	       			matrix.setElementValue(iB, iA, -1.0D / A.resistance);
				}
			}else if (columnNode instanceof SwitchB){
				//Diagonal element
				if (((SwitchB) columnNode).A.isOn)
					diagonalElement += 1.0D / ((SwitchB) columnNode).A.resistance;
			}
			
			
        	//Transformer
        	else if (columnNode instanceof TransformerPrimary){
       			TransformerPrimary pri = (TransformerPrimary) columnNode;
       			int iPri = columnIndex;
       			int iSec = unknownVoltageNodes.indexOf(pri.secondary);
       			
       			double ratio = pri.ratio;
       			double res = pri.rsec;
       			//Primary diagonal element
       			diagonalElement += ratio*ratio/res;
       			
       			//Off-diagonal elements
       			matrix.setElementValue(iPri, iSec, -ratio / res);
       			matrix.setElementValue(iSec, iPri, -ratio / res);
			}
			else if (columnNode instanceof TransformerSecondary){
				//Secondary diagonal element
       			diagonalElement += 1.0D / ((TransformerSecondary) columnNode).primary.rsec;
			}
        	
        	//Diode
			else if (columnNode instanceof DiodeInput){
    			DiodeInput input = (DiodeInput) columnNode;
    			
    			int iPri = columnIndex;
    			int iSec = unknownVoltageNodes.indexOf(input.output);	
    			double Vd = voltages[iPri]-voltages[iSec];
    			
    			double Vt = input.Vt;
    			double Is = input.Is;
    			double Rmin = input.Rs;
    			
    			double Gd;
    			
    			if (Vd>Vt*Math.log(Vt/Is/Rmin)){
    				Gd = 1.0D/Rmin + Gpn;
    				diagonalElement += 1.0D/Rmin + Gpn;
    			}else{
    				Gd = Is/Vt*Math.exp(Vd/Vt) + Gpn;
    				diagonalElement += Is/Vt*Math.exp(Vd/Vt) + Gpn;
    			}
    			
    			matrix.setElementValue(iPri, iSec, -Gd);
    			matrix.setElementValue(iSec, iPri, -Gd);
			}
			else if (columnNode instanceof DiodeOutput){
    			DiodeInput input = ((DiodeOutput) columnNode).input;

    			int iPri = unknownVoltageNodes.indexOf(input);
    			int iSec = columnIndex;
    			double Vd = voltages[iPri]-voltages[iSec];
    			
    			double Vt = input.Vt;
    			double Is = input.Is;
    			double Rmin = input.Rs;

    			if (Vd>Vt*Math.log(Vt/Is/Rmin)){
    				diagonalElement += 1.0D/Rmin + Gpn;
    			}else{
    				diagonalElement += Is/Vt*Math.exp(Vd/Vt) + Gpn;
    			}
			}
        	
        	
        	//Regulator
			else if (columnNode instanceof RegulatorInput){
				RegulatorInput input = (RegulatorInput) columnNode;
    			RegulatorController controller = input.controller;
				
    			int iIn = columnIndex;
    			int iOut = unknownVoltageNodes.indexOf(input.output);
    			int iCon = unknownVoltageNodes.indexOf(controller);
    			
    			double Vi = voltages[iIn];
    			double Vo = voltages[iOut];
    			double Vc = voltages[iCon];
    			double Ro = input.Ro;
    			double Dmax = controller.input.Dmax;
    			
    			diagonalElement += (Vc+Dmax)*(Vc+Dmax)/Ro;

    			matrix.setElementValue(iIn, iOut, -(Vc+Dmax)/Ro);
    			matrix.setElementValue(iOut, iIn, -(Vc+Dmax)/Ro);
    			matrix.setElementValue(iCon, iOut, -Vi/Ro);
    			matrix.setElementValue(iOut, iCon, controller.input.A/controller.input.Rs);
    			matrix.setElementValue(iCon, iIn, (2*Vi*(Vc+Dmax) - Vo)/Ro);
			}else if (columnNode instanceof RegulatorOutput){
    			diagonalElement += 1.0D / ((RegulatorOutput) columnNode).input.Ro;
    			diagonalElement += 1.0D / ((RegulatorOutput) columnNode).input.Rdummy;
			}else if (columnNode instanceof RegulatorController){
				RegulatorController controller = (RegulatorController) columnNode;
				RegulatorInput input = controller.input;
    			
    			int iIn = unknownVoltageNodes.indexOf(input);
    			int iOut = unknownVoltageNodes.indexOf(input.output);
    			int iCon = columnIndex;
    			
    			double Vi = voltages[iIn];
    			double Vo = voltages[iOut];
    			double Vc = voltages[iCon];
    			double Rs = controller.input.Rs;
    			double Rmin = controller.input.Rc;
    			
    			if (Vc>Vt*Math.log(Vt/Is/Rmin))
    				diagonalElement += 1.0D/Rs + 1.0D/Rmin;
    			else
    				diagonalElement += 1.0D/Rs + Is/Vt*Math.exp(Vc/Vt);
			}
        	
        	matrix.setElementValue(columnIndex, columnIndex, diagonalElement);
        }

        matrix.finishEditing();
    }
    
    public void runSimulator(boolean optimizeGraph) {
    	if (optimizeGraph)
    		dataProvider.getTEGraph().optimizGraph();
    	
        LinkedList<SEComponent> unknownVoltageNodes = dataProvider.getTEGraph().getTerminalNodes();
    	int matrixSize = 0;
    	Iterator<SEComponent> iterator = unknownVoltageNodes.iterator();
    	while(iterator.hasNext()){
    		iterator.next().index = matrixSize;
    		matrixSize++;
    	}
    	
    	double[] voltages = new double[matrixSize];
    	double[] currents;
    	   		
        iterations = 0;
        while(true) {
        	//Calculate the current flow into each node using their voltage
        	currents = calcCurrents(voltages, unknownVoltageNodes);	//Current mismatch
        	
        	boolean keepGoing = false;
            
            for (int i = 0; i < matrixSize; i++) {
                if (Math.abs(currents[i]) > epsilon)
                	keepGoing = true;
            }      		

            
            if (keepGoing){
            	if (iterations > ConfigManager.maxIteration){
            		SEUtils.logInfo("Maximum number of iteration has reached, something must go wrong!");
            		break;
            	}
            }else{
            	break;
            }
        	
        	formJacobian(voltages, unknownVoltageNodes);
        	//matrix.print();
            if (!matrix.solve(currents)){
            	throw new RuntimeException("Due to incorrect value of components, the energy net has been shutdown!");
            }
            //currents is now deltaV
            
            //Debug.println("Iteration:", String.valueOf(iterations));
            for (int i = 0; i < matrixSize; i++) {
            	if (!Double.isNaN(currents[i]))
            		voltages[i] += currents[i];
            	//String[] temp = unknownVoltageNodes.get(i).toString().split("[.]");
            	//Debug.println(temp[temp.length-1].split("@")[0], String.valueOf(voltages[i]));
            }
      
            iterations++;
        }

        int i = 0;
        for (SEComponent node: unknownVoltageNodes){
        	node.voltageCache = voltages[i];
        	i++;
        }
        
        SEUtils.logInfo("Run!" + String.valueOf(iterations));        
    }
}