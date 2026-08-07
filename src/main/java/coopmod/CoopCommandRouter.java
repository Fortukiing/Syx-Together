package coopmod;

import game.GAME;
import view.main.VIEW;
import view.tool.PLACABLE;

final class CoopCommandRouter {

	private CoopCommandRouter() {
	}

	static void apply(String line) {
		if (line == null || line.length() == 0)
			return;
		String[] p = line.split("\t", -1);
		try {
			CoopRuntime.beginRemoteApply();
			if ("A".equals(p[0]) && p.length >= 2) {
				PLACABLE pl = CoopRuntime.find(CoopProtocol.dec(p[1]));
				if (pl != null) {
					CoopRuntime.register(pl);
					VIEW.s().tools.place(pl);
				} else {
					CoopRuntime.log("Missing placable for activation: " + CoopProtocol.dec(p[1]));
				}
			} else if ("M".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyMulti(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), CoopProtocol.dec(p[3]));
			} else if ("RM".equals(p[0]) && p.length >= 6) {
				CoopRuntime.applyRoomMulti(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), CoopProtocol.dec(p[5]));
			} else if ("RM".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyRoomMulti(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), 0, Integer.parseInt(p[3]), CoopProtocol.dec(p[4]));
			} else if ("RI".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyRoomInit(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]));
			} else if ("RC".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyRoomCreate(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]));
			} else if ("RF".equals(p[0]) && p.length >= 8) {
				CoopRuntime.applyRoomFixed(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]));
			} else if ("RFIN".equals(p[0]) && p.length >= 3) {
				if (!CoopRuntime.applyRoomConstructionFinished(Integer.parseInt(p[1]), Integer.parseInt(p[2]), true))
					CoopRuntime.queuePendingFinish(CoopPendingFinish.roomFinish(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			} else if ("RJ".equals(p[0]) && p.length >= 5) {
				if (!CoopRuntime.applyRoomJobFinished(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), true))
					CoopRuntime.queuePendingFinish(CoopPendingFinish.roomJob(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
			} else if ("CF".equals(p[0]) && p.length >= 3) {
				if (!CoopRuntime.applyRoomConstructionFinished(Integer.parseInt(p[1]), Integer.parseInt(p[2]), true))
					CoopRuntime.queuePendingFinish(CoopPendingFinish.roomFinish(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			} else if ("JB".equals(p[0]) && p.length >= 4) {
				if (!CoopRuntime.applyJobBuildFinished(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), true))
					CoopRuntime.queuePendingFinish(CoopPendingFinish.buildJob(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
			} else if ("MSG".equals(p[0]) && p.length >= 2) {
				CoopRuntime.applyMessage(CoopProtocol.dec(p[1]));
			} else if ("EVS".equals(p[0]) && p.length >= 8) {
				GAME.EVENT().coopApplyEventStart(Integer.parseInt(p[1]), CoopProtocol.dec(p[2]), CoopProtocol.deserializeContext(CoopProtocol.dec(p[7])), "1".equals(p[3]), "1".equals(p[4]), "1".equals(p[5]), "1".equals(p[6]));
			} else if ("EVT".equals(p[0]) && p.length >= 4) {
				GAME.EVENT().coopApplyTemporaryEvent(Integer.parseInt(p[1]), CoopProtocol.dec(p[2]), CoopProtocol.deserializeContext(CoopProtocol.dec(p[3])));
			} else if ("EVE".equals(p[0])) {
				GAME.EVENT().coopApplyEventClear();
			} else if ("EVC".equals(p[0]) && p.length >= 5) {
				GAME.EVENT().coopApplyChoice(Integer.parseInt(p[1]), CoopProtocol.dec(p[2]), Integer.parseInt(p[3]), CoopProtocol.deserializeContext(CoopProtocol.dec(p[4])));
			} else if ("CR".equals(p[0]) && p.length >= 2) {
				CoopRuntime.applyCreditsSync(Long.parseLong(p[1]) / 100.0);
			} else if ("US".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyUiSanitySync(p[1], CoopProtocol.dec(p[2]), CoopProtocol.dec(p[3]));
			} else if ("DP".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyDiplomacy(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("DPS".equals(p[0]) && p.length >= 2) {
				CoopRuntime.applyDiplomacyDealSnapshot(p[1]);
			} else if ("TECH".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyTechLevel(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("TBI".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyTradeImportSettings(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("TBE".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyTradeExportSettings(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("REN".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyRoomEmploymentNeeded(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("RAE".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyRoomAutoEmploy(Integer.parseInt(p[1]), Integer.parseInt(p[2]), "1".equals(p[3]));
			} else if ("REP".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyRoomEmploymentPriority(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("REGP".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyRoomEmploymentGroupPriority(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("GPF".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyFoodPermission(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), "1".equals(p[4]));
			} else if ("GCE".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyCivicEquipmentTarget(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GHF".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyHomeFurnitureTarget(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GSP".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyServicePermission(Integer.parseInt(p[1]), Integer.parseInt(p[2]), "1".equals(p[3]));
			} else if ("GRP".equals(p[0]) && p.length >= 6) {
				CoopRuntime.applyReligionPermission(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), "1".equals(p[5]));
			} else if ("GGP".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyGravePermission(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), "1".equals(p[4]));
			} else if ("GIA".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyImmigrationAuto(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("GRL".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyReproductionLimit(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("GRF".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyReproductionForced(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("GEL".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyEducationLimit(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GSD".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyStatDecree(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GMA".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyStatMultiplierAuto(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GMM".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyStatMultiplierMark(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GMU".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyStatMultiplierUnmark(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("GLP".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyLawPunishment(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("GHS".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyHomeSetting(Integer.parseInt(p[1]), Integer.parseInt(p[2]), CoopProtocol.dec(p[3]));
			} else if ("GDM".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyDivisionMen(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("GDR".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyDivisionRace(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("GDT".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyDivisionTraining(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]) / 10000.0);
			} else if ("GDE".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyDivisionEquipment(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("GDB".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyDivisionBanner(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			} else if ("GGA".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyGuardActiveDuty(Integer.parseInt(p[1]), "1".equals(p[2]));
			} else if ("WM".equals(p[0]) && p.length >= 6) {
				CoopRuntime.applyArmyMove(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
			} else if ("WB".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyArmyBesiege(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("WRG".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyArmyRaidRegion(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
			} else if ("WRT".equals(p[0]) && p.length >= 5) {
				CoopRuntime.applyArmyRaidToggle(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), "1".equals(p[4]));
			} else if ("WI".equals(p[0]) && p.length >= 7) {
				CoopRuntime.applyArmyIntercept(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
			} else if ("WS".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyArmyStop(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("WD".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyArmyDisband(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("SC".equals(p[0]) && p.length >= 7) {
				CoopRuntime.applyStorageCrate(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
			} else if ("GR".equals(p[0]) && p.length >= 6) {
				CoopRuntime.applyScatteredResource(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
			} else if ("F".equals(p[0]) && p.length >= 6) {
				CoopRuntime.applyFixed(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
			} else if ("S".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applySimple(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("T".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applySimpleTile(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("G".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applySingle(CoopProtocol.dec(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} else if ("V".equals(p[0]) && p.length >= 2) {
				GAME.SPEED.speedSet(Double.parseDouble(p[1]));
			} else if ("N".equals(p[0]) && p.length >= 2) {
				CoopRuntime.applyNpcSync(CoopProtocol.dec(p[1]));
			} else if ("N2".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyNpcSnapshot(Long.parseLong(p[1]), Long.parseLong(p[2]), p[3]);
			} else if ("NL".equals(p[0]) && p.length >= 2) {
				CoopRuntime.applyNpcLogic(p[1]);
			} else if ("A2".equals(p[0]) && p.length >= 4) {
				CoopRuntime.applyAnimalSnapshot(Long.parseLong(p[1]), Long.parseLong(p[2]), p[3]);
			} else if ("P".equals(p[0]) && p.length >= 9) {
				CoopCursor.applyRemote(p);
			} else if ("U".equals(p[0]) && p.length >= 3) {
				CoopRuntime.applyWorldSync(p[1], CoopProtocol.dec(p[2]));
			}
		} catch (Exception e) {
			CoopRuntime.remoteApplyFailed(line, e);
		} catch (Error e) {
			CoopRuntime.remoteApplyFailed(line, e);
		} finally {
			CoopRuntime.endRemoteApply();
		}
	}
}
