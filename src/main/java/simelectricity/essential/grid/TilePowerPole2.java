package simelectricity.essential.grid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import simelectricity.essential.client.grid.PowerPoleRenderHelper;
import simelectricity.essential.utils.math.SEMathHelper;
import simelectricity.essential.utils.math.Vec3f;

public class TilePowerPole2 extends TilePowerPole {	
	@Override
	@SideOnly(Side.CLIENT)
	protected PowerPoleRenderHelper createRenderHelper(){
		PowerPoleRenderHelper helper;
		int rotation = (getBlockMetadata() & 3) * 2;
		
		if (isSpecial()) {
			helper = new PowerPoleRenderHelper(world, pos, rotation, 2, 3) {
				@Override
				public void updateRenderData(BlockPos... neighborPosList) {
					super.updateRenderData(neighborPosList);
					
					if (connectionInfo.size() < 2)
						return;
					
		    		ConnectionInfo[] connection1 = this.connectionInfo.getFirst();
		    		ConnectionInfo[] connection2 = this.connectionInfo.getLast();
		    		
		    		Vec3f pos = new Vec3f(
		    		3.95F * MathHelper.sin(this.rotation/180F*SEMathHelper.PI) + 0.5F + this.pos.getX(),
		    		this.pos.getY() + 23 -18,
		    		3.95F * MathHelper.cos(this.rotation/180F*SEMathHelper.PI) + 0.5F + this.pos.getZ()
		    		);
		    		
		    		addExtraWire(connection1[1].fixedFrom, connection2[1].fixedFrom, 2.5F);
		    		if (PowerPoleRenderHelper.hasIntersection(
		    				connection1[0].fixedFrom, connection2[0].fixedFrom,
		    				connection1[2].fixedFrom, connection2[2].fixedFrom)) {
		    			addExtraWire(connection1[0].fixedFrom, connection2[2].fixedFrom, 2.5F);
		    			addExtraWire(connection1[2].fixedFrom, connection2[0].fixedFrom, 2.5F);
		    		}else {
		    			addExtraWire(connection1[0].fixedFrom, connection2[0].fixedFrom, 2.5F);
		    			addExtraWire(connection1[2].fixedFrom, connection2[2].fixedFrom, 2.5F);
		    		}
				}
			};
			helper.addInsulatorGroup(-0.25F, 0.125F, 0,
					helper.createInsulator(2, 3, -0.25F, 0.125F, -4.5F),
					helper.createInsulator(2, 3, -0.25F, 0.125F, 0),
					helper.createInsulator(2, 3, -0.25F, 0.125F, 4.5F)
					);
			helper.addInsulatorGroup(0.25F, 0.125F, 0,
					helper.createInsulator(2, 3, 0.25F, 0.125F, -4.5F),
					helper.createInsulator(2, 3, 0.25F, 0.125F, 0),
					helper.createInsulator(2, 3, 0.25F, 0.125F, 4.5F)
					);
		}else {
			helper = new PowerPoleRenderHelper(world, pos, rotation, 1, 3);
			helper.addInsulatorGroup(0, 0.125F-1.95F, 0F,
					helper.createInsulator(0, 3, 0, 0.125F-1.95F, -4.5F),
					helper.createInsulator(0, 3, 0, 0.125F-1.95F, 0F),
					helper.createInsulator(0, 3, 0, 0.125F-1.95F, 4.5F)
					);
		}

		return helper;
	}
}