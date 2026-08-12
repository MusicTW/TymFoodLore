package club.timehut.foodlore

import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.inventory.ItemStack
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class FoodItemSpawnListenerTest {
    @Test
    fun `normalizes food created directly in the world`() {
        val stack = mock(ItemStack::class.java)
        val entity = mock(Item::class.java)
        `when`(entity.itemStack).thenReturn(stack)
        var normalized: ItemStack? = null
        val listener = FoodItemSpawnListener {
            normalized = it
            true
        }

        listener.onItemSpawn(ItemSpawnEvent(entity))

        assertSame(stack, normalized)
        verify(entity).itemStack = stack
    }

    @Test
    fun `registers the spawn hook as a Bukkit event handler`() {
        val method = FoodItemSpawnListener::class.java.getDeclaredMethod(
            "onItemSpawn",
            ItemSpawnEvent::class.java,
        )

        assertTrue(method.isAnnotationPresent(EventHandler::class.java))
    }
}
