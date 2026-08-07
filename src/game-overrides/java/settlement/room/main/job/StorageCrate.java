package settlement.room.main.job;

import static settlement.main.SETT.PATH;

import java.io.Serializable;

import coopmod.CoopRuntime;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.main.SETT;
import settlement.misc.util.RESOURCE_TILE;
import settlement.misc.util.TILE_STORAGE;
import settlement.room.main.ROOMA;
import settlement.room.main.RoomInstance;
import snake2d.Errors;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;

public abstract class StorageCrate implements RESOURCE_TILE, TILE_STORAGE{

	final static int noRes = 0x0;
	private int tx, ty;
	private StorageData data;
	private RoomInstance ins;
	
	protected StorageCrate(){
		if (RESOURCES.ALL().size() +1 > 0x0FF) {
			throw new Errors.GameError("Too many resources are declared: " + RESOURCES.ALL().size());
		}
	}
	
	public StorageData[] make(ROOMA room) {
		int am = 0;
		for (COORDINATE c : room.body()) {
			if (room.is(c) && is(c.x(), c.y())) {
				SETT.ROOMS().data.set(room, c, am);
				am++;
			}
		}
		StorageData[] res = new StorageData[am];
		for (int i = 0; i < res.length; i++) {
			res[i] = new StorageData();
		}
		return res;
	}
	
	public StorageCrate get(int tx, int ty, RoomInstance ins, StorageData[] data) {
		if (is(tx, ty)) {
			int i = SETT.ROOMS().data.get(tx, ty);
			this.tx = tx;
			this.ty = ty;
			this.data = data[i];
			this.ins = ins;
			return this;
		}
		return null;
	}
	
	protected abstract boolean is(int tx, int ty);
	
	protected abstract int max(RoomInstance ins);
	
	@Override
	public void storageDeposit(int amount) {
		if (amount == 0)
			return;
		if (resource() == null || amount() + amount > max(ins))
			throw new RuntimeException(resource() + " " + amount() + " " + amount + " " + max(ins));
		reservedSpaceSet(reservedSpace()-amount);
		amountSet(amount()+amount);
	}

	@Override
	public int storageReserved() {
		if (resource() == null)
			return 0;
		return data.bReservedSpace;
	}

	@Override
	public int storageReservable() {
		if (resource() == null)
			return 0;
		return max(ins) - (amount() + storageReserved());
	}

	@Override
	public void storageReserve(int amount) {
		if (storageReservable() < amount)
			throw new RuntimeException(storageReservable() + " " + amount);
			
		reservedSpaceSet(reservedSpace()+amount);
	}

	@Override
	public void storageUnreserve(int amount) {
		if (storageReserved() < amount)
			amount = storageReserved();
		reservedSpaceSet(storageReserved()-amount);
	}
	
	@Override
	public RESOURCE resource() {
		int i = data.res;
		if (i == noRes || i > RESOURCES.ALL().size())
			return null;
		return RESOURCES.ALL().get(i-1);
	}
	
	public void resourceSet(RESOURCE res) {
		if (resource() != null) {
			throw new RuntimeException();
		}
		data.res = (short) (res.index()+1);
		add();
		CoopRuntime.storageCrateChanged(this);
	}
	
	public int amount(int tx, int ty, RoomInstance ins, StorageData[] data) {
		if (is(tx, ty)) {
			int i = SETT.ROOMS().data.get(tx, ty);
			return data[i].bAmount;
		}
		return 0;
	}
	
	public RESOURCE res(int tx, int ty, RoomInstance ins, StorageData[] data) {
		if (is(tx, ty)) {
			int i = SETT.ROOMS().data.get(tx, ty);
			i = data[i].res;
			if (i == noRes || i > RESOURCES.ALL().size())
				return null;
			return RESOURCES.ALL().get(i-1);
		}
		return null;
	}
	
	public void remove() {
		RESOURCE r = resource();
		if (r != null) {
			count(-1);
			if (findableReservedCanBe())
				PATH().finders.resource.reportAbsence(this);
			if (storageReservable() > 0)
				PATH().finders.storage.reportAbsence(this);
		}
	}
	
//	public void fix() {
//		if (resource() == null) {
//			data.bAmount = 0;
//			data.bReserved = 0;
//			data.bReservedSpace = 0;
//		}else {
//			count(resource().bIndex(), 1, data.bAmount, data.bAmount - data.bReserved, data.bReservedSpace);
//		}
//			
//	}
	
	public void add() {
		RESOURCE r = resource();
		if (r != null) {
			count(1);
			if (findableReservedCanBe()) {
				PATH().finders.resource.reportPresence(this);
			}
			if (storageReservable() > 0) {
				PATH().finders.storage.reportPresence(this);
			}
		}
	}
	
	protected abstract void count(int delta);

	@Override
	public int amount() {
		return data.bAmount;
	}
	
	public void amountSet(int am) {
		remove();
		data.bAmount = (short) am;
		add();
		CoopRuntime.storageCrateChanged(this);
	}
	
	public int reserved() {
		return data.bReserved;
	}
	
	public void reservedSet(int r) {
		remove();
		data.bReserved = (short) r;
		add();
		CoopRuntime.storageCrateChanged(this);
	}
	
	public int reservedSpace() {
		return data.bReservedSpace;
	}
	
	private void reservedSpaceSet(int r) {
		remove();
		data.bReservedSpace = (short) r;
		add();
		CoopRuntime.storageCrateChanged(this);
	}
	
	@Override
	public int y() {
		return ty;
	}
	
	@Override
	public int x() {
		return tx;
	}
	
	@Override
	public boolean findableReservedIs() {
		return data.bReserved > 0;
	}
	
	@Override
	public boolean findableReservedCanBe() {
		return data.bReserved < data.bAmount;
	}
	
	@Override
	public void findableReserveCancel() {
		if (reserved() > 0)
			reservedSet(reserved()-1);
	}
	
	@Override
	public void findableReserve() {
		reservedSet(reserved()+1);
	}
	
	@Override
	public void resourcePickup() {
		findableReserveCancel();
		amountSet(amount()-1);
	}
	
	@Override
	public int reservable() {
		return StorageCrate.this.amount()-StorageCrate.this.reserved();
	}
	
	@Override
	public double spoilRate() {
		return spoilRate(ins);
	}
	
	protected abstract double spoilRate(RoomInstance ins);
	
	public void clear() {
		if (resource() == null)
			return;
		int am = amount();
		remove();
		if (am > 0) {
			for (DIR dd : DIR.ORTHO) {
				if (!PATH().solidity.is(this, dd)) {
					SETT.THINGS().resources.create(x()+dd.x(), y()+dd.y(), resource(), am);
					break;
				}
			}
		}
		data.bAmount = 0;
		data.bReserved = 0;
		data.bReservedSpace = 0;
		data.res = noRes;
		CoopRuntime.storageCrateChanged(this);
	}
	
	public void dispose(){
		RESOURCE res = resource();
		if (res == null)
			return;
		int am = amount();
		remove();
		
		for (DIR d : DIR.ORTHO) {
			if (SETT.IN_BOUNDS(tx, ty, d) && !SETT.PATH().solidity.is(tx, ty, d)) {
				SETT.THINGS().resources.create(tx+d.x(), ty+d.y(), res, am);
				am = 0;
				break;
			}
		}
		
		if (am > 0)
			SETT.THINGS().resources.create(tx, ty, res, am);
		data.bAmount = 0;
		data.bReserved = 0;
		data.bReservedSpace = 0;
		data.res = noRes;
		CoopRuntime.storageCrateChanged(this);
	}
	
	public void disposeSilent(){
		data.bAmount = 0;
		data.bReserved = 0;
		data.bReservedSpace = 0;
		data.res = noRes;
		CoopRuntime.storageCrateChanged(this);
	}
	
	@Override
	public boolean isFindable() {
		return true;
	}

	public static class StorageData implements Serializable{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		short res;
		short bAmount;
		short bReserved;
		short bReservedSpace;
		
		private StorageData() {
			
		}
		
		public void clear() {
			res = 0;
			bAmount = 0;
			bReserved = 0;
			bReservedSpace = 0;
		}

	}

}
