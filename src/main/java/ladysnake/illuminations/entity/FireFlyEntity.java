package ladysnake.illuminations.entity;

import java.util.HashMap;

import ladysnake.illuminations.mod.ConfigData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

public class FireFlyEntity extends LightOrbEntity implements IEntityAdditionalSpawnData {

    private static final DataParameter<Float> SCALE_MODIFIER = EntityDataManager.createKey(PlayerEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> COLOR_MODIFIER = EntityDataManager.createKey(PlayerEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> ALPHA_MODIFIER = EntityDataManager.createKey(PlayerEntity.class, DataSerializers.FLOAT);

    private static final DataParameter<Integer> RANDOM_TICK_OFFSET = EntityDataManager.createKey(PlayerEntity.class, DataSerializers.VARINT);

    protected boolean canDespawn;
    protected boolean isAttractedByLight;
    private Float nextAlphaGoal;

    // Behavior : looking for nearby lit up blocks
    private double groundLevel;
    private BlockPos lightTarget;
    private double xTarget;
    private double yTarget;
    private double zTarget;
    private int targetChangeCooldown = 0;

    public FireFlyEntity(EntityType<? extends MobEntity> type, World world) {

        super(type, world);

        this.canDespawn = true;
        this.isAttractedByLight = true;

        this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1.0D);

    }

    // auto syncs values across server and client
    @Override
    protected void registerData()
    {

        super.registerData();
        this.dataManager.register(SCALE_MODIFIER, 1F);
        this.dataManager.register(COLOR_MODIFIER, 1F);
        this.dataManager.register(ALPHA_MODIFIER, 1F);
        this.dataManager.register(RANDOM_TICK_OFFSET, 20);
    }

    // set data once, and only when joining the world. this one is called and sets
    // the scale and color correctly. onInitialSpawn isnt being called AFAIK
    @Override
    public void onAddedToWorld()
    {

        this.dataManager.set(SCALE_MODIFIER, 0.1F + this.rand.nextFloat() * 0.15F);
        this.dataManager.set(COLOR_MODIFIER, 0.25F + this.rand.nextFloat() * 0.75F);
        this.dataManager.set(RANDOM_TICK_OFFSET, this.rand.nextInt(20) + 20);

        super.onAddedToWorld();
    }

    // this one is never called to my testing. need to find out where it comes from.
    // will keep the info here anyway
    @Override
    public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, ILivingEntityData spawnDataIn, CompoundNBT dataTag)
    {

        this.dataManager.set(SCALE_MODIFIER, 0.1F + this.rand.nextFloat() * 0.15F);
        this.dataManager.set(COLOR_MODIFIER, 0.25F + this.rand.nextFloat() * 0.75F);
        this.dataManager.set(RANDOM_TICK_OFFSET, this.rand.nextInt(20) + 20);

        return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    public float getScaleModifier()
    {

        return this.dataManager.get(SCALE_MODIFIER);
    }

    public float getColorModifier()
    {

        return this.dataManager.get(COLOR_MODIFIER);
    }

    public float getAlpha()
    {

        return this.dataManager.get(ALPHA_MODIFIER);
    }

    public void setAlpha(float alpha)
    {

        this.dataManager.set(ALPHA_MODIFIER, alpha);
    }

    public Float getNextAlphaGoal()
    {

        return nextAlphaGoal;
    }

    public void setNextAlphaGoal(Float nextAlphaGoal)
    {

        this.nextAlphaGoal = nextAlphaGoal;
    }

    public boolean isAttractedByLight()
    {

        return isAttractedByLight;
    }

    @Override
    public void writeAdditional(CompoundNBT compound)
    {

        compound.putFloat("scale", getScaleModifier());
        compound.putFloat("color", getColorModifier());
        compound.putFloat("alpha", getAlpha());
        compound.putBoolean("atracted", isAttractedByLight);
        compound.putInt("tickoffset", dataManager.get(RANDOM_TICK_OFFSET));
        
        super.writeAdditional(compound);
    }

    @Override
    public void readAdditional(CompoundNBT compound)
    {

        this.dataManager.set(SCALE_MODIFIER, compound.getFloat("scale"));
        this.dataManager.set(COLOR_MODIFIER, compound.getFloat("color"));
        this.dataManager.set(RANDOM_TICK_OFFSET , compound.getInt("tickoffset"));
        setAlpha(compound.getFloat("alpha"));

        super.readAdditional(compound);
    }

    @Override
    public boolean canSpawn(IWorld world, SpawnReason spawnReasonIn)
    {

        if (this.world.isDaytime())
            return false;
        if (this.world.isThundering())
            return false;

        return true;
    }

    @Override
    public void tick()
    {

        super.tick();

        // only check once every second to prevent (some) lag
        if (this.world.getGameTime() % dataManager.get(RANDOM_TICK_OFFSET) >= 0)
            return;

        // kill when further away then x chunks
        boolean anyPlayerCloseBy = world.isPlayerWithin(this.serverPosX, this.serverPosY, this.serverPosZ, ConfigData.despawn_firefly);

        if (!anyPlayerCloseBy)
            this.remove();
        
        long dayTime = this.world.getWorldInfo().getDayTime();
        if (dayTime > 1000 && dayTime < 12990)
            this.remove();

        if (this.isBurning())
            this.remove();

        this.targetChangeCooldown -= (this.getPositionVector().squareDistanceTo(this.prevPosX, this.prevPosY, this.prevPosZ) < 0.0125) ? 10 : 1;

        if ((xTarget == 0 && yTarget == 0 && zTarget == 0) || this.getPosition().distanceSq(xTarget, yTarget, zTarget, true) < 9 || targetChangeCooldown <= 0)
            selectBlockTarget();

        Vec3d targetVector = new Vec3d(this.xTarget - this.prevPosX, this.yTarget - this.prevPosY, this.zTarget - this.prevPosZ);
        double length = targetVector.length();
        targetVector = targetVector.scale(0.1 / length);

        if (!this.world.getBlockState(new BlockPos(this.prevPosX, this.prevPosY - 0.1, this.prevPosZ)).getBlock().canSpawnInBlock())
            this.setMotion((0.9) * getMotion().x + (0.1) * targetVector.x, 0.05, (0.9) * getMotion().z + (0.1) * targetVector.z);
        else
            this.setMotion((0.9) * getMotion().x + (0.1) * targetVector.x, (0.9) * getMotion().y + (0.1) * targetVector.y,
                    (0.9) * getMotion().z + (0.1) * targetVector.z);

        if (this.getPosition() != this.getTargetPosition())
            this.move(MoverType.SELF, this.getMotion());
    }

    private void selectBlockTarget()
    {

        if (this.lightTarget == null || !this.isAttractedByLight())
        {
            this.groundLevel = 0;
            for (int i = 0; i < 20; i++)
            {
                BlockState checkedBlock = this.world.getBlockState(new BlockPos(this.serverPosX, this.serverPosY - i, this.serverPosZ));
                if (!checkedBlock.getBlock().canSpawnInBlock())
                {
                    this.groundLevel = this.serverPosY - i;
                }
                if (this.groundLevel != 0)
                    break;
            }

            this.xTarget = this.serverPosX + this.rand.nextGaussian() * 10;
            this.yTarget = Math.min(Math.max(this.serverPosY + this.rand.nextGaussian() * 2, this.groundLevel), this.groundLevel + 4);
            this.zTarget = this.serverPosZ + this.rand.nextGaussian() * 10;

            if (this.world.getBlockState(new BlockPos(this.xTarget, this.yTarget, this.zTarget)).getBlock().canSpawnInBlock())
                this.yTarget += 1;

            if (this.world.func_226658_a_(LightType.SKY, this.getPosition()) > 8 && !this.world.isDaytime())
                this.lightTarget = getRandomLitBlockAround();
        }
        else
        {
            this.xTarget = this.lightTarget.getX() + this.rand.nextGaussian();
            this.yTarget = this.lightTarget.getY() + this.rand.nextGaussian();
            this.zTarget = this.lightTarget.getZ() + this.rand.nextGaussian();
            //getLightFor = func 226658
            if (this.world.func_226658_a_(LightType.BLOCK, this.getPosition()) > 8)
            {
                BlockPos possibleTarget = getRandomLitBlockAround();
                if (this.world.func_226658_a_(LightType.BLOCK, possibleTarget) > this.world.func_226658_a_(LightType.BLOCK, this.lightTarget))
                    this.lightTarget = possibleTarget;
            }

            if (this.world.func_226658_a_(LightType.BLOCK, this.getPosition()) <= 8 || this.world.isDaytime())
                this.lightTarget = null;
        }

        targetChangeCooldown = this.rand.nextInt() % 100;
    }

    private BlockPos getRandomLitBlockAround()
    {

        HashMap<BlockPos, Integer> randBlocks = new HashMap<>();
        for (int i = 0; i < 15; i++)
        {
            BlockPos randBP = new BlockPos(this.serverPosX + this.rand.nextGaussian() * 10, this.serverPosY + this.rand.nextGaussian() * 10,
                    this.serverPosZ + this.rand.nextGaussian() * 10);
            randBlocks.put(randBP, this.world.func_226658_a_(LightType.BLOCK, randBP));
        }
        return randBlocks.entrySet().stream().max((entry1, entry2) -> entry1.getValue() > entry2.getValue() ? 1 : -1).get().getKey();
    }

    private BlockPos getTargetPosition()
    {

        return new BlockPos(this.xTarget, this.yTarget + 0.5, this.zTarget);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer)
    {

        buffer.writeFloat(getScaleModifier()); // scale
        buffer.writeFloat(getColorModifier());// color
        buffer.writeFloat(1F);// alpha
    }

    @Override
    public void readSpawnData(PacketBuffer buffer)
    {

        this.dataManager.set(SCALE_MODIFIER, buffer.readFloat());
        this.dataManager.set(COLOR_MODIFIER, buffer.readFloat());
        this.dataManager.set(ALPHA_MODIFIER, buffer.readFloat());

    }
}
