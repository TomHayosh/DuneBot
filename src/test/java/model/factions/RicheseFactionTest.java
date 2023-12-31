package model.factions;

import constants.Emojis;
import model.Game;
import model.Territory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RicheseFactionTest {

    private Game game;
    private RicheseFaction faction;

    @BeforeEach
    void setUp() {
        game = new Game();
        faction = new RicheseFaction("player", "player", game);
    }

    @Test
    public void testInitialSpice() { assertEquals(faction.getSpice(), 5); }

    @Test
    public void testFreeRevivals() { assertEquals(faction.getFreeRevival(), 2); }

    @Test
    public void testInitialHasMiningEquipment() { assertFalse(faction.hasMiningEquipment()); }

    @Test
    public void testInitialReserves() {
        assertEquals(faction.getReserves().getStrength(), 20);
        assertEquals(faction.getReserves().getName(), "Richese");
    }

    @Test
    public void testInitialForcePlacement() {
        for (String territoryName : game.getTerritories().keySet()) {
            Territory territory = game.getTerritories().get(territoryName);

            assertEquals(territory.getForces().size(), 0);
        }
    }

    @Test
    public void testEmoji() { assertEquals(faction.getEmoji(), Emojis.RICHESE); }

    @Test
    public void testHandLimit() { assertEquals(faction.getHandLimit(), 4); }
}