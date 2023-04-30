package net.elytrarace.utils.builder

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import com.google.common.annotations.Beta
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Repairable
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import java.util.*

/* It's a builder class for ItemStacks */
open class ItemBuilder() {
    /* It's a secondary constructor that takes a lambda as a parameter. */
    constructor(init: ItemBuilder.() -> Unit) : this() {
        init()
    }

    protected var itemStack: ItemStack = ItemStack(Material.AIR)

    /**
     * `material` is a function that takes a function as a parameter, and that function returns a Material
     *
     * @param materialAttr A function that returns a Material.
     */
    fun material(materialAttr:  Material) {
        itemStack = ItemStack(materialAttr)
    }

    /**
     * `itemStack` is a function that takes a function as a parameter, and that function returns an `ItemStack`
     *
     * @param stackAttr () -> ItemStack
     */
    fun itemStack(stackAttr: ItemStack) {
        itemStack = stackAttr
    }

    /**
     * `itemStack.apply { this.amount = amountAttr() }`
     *
     * @param amountAttr () -> Int
     */
    fun amount(amountAttr: Int) {
        itemStack.apply {
            this.amount = amountAttr
        }
    }

    /**
     * `itemStack.editMeta { it.addEnchant(enchantmentAttr(), levelAttr(), ignoreRestrictionsAttr()) }`
     *
     * @param enchantmentAttr () -> Enchantment
     * @param levelAttr () -> Int
     * @param ignoreRestrictionsAttr This is a boolean that determines whether or not the enchantment should be applied
     * even if the item doesn't support it.
     */
    fun enchantment(enchantmentAttr: Enchantment, levelAttr: Int, ignoreRestrictionsAttr:  Boolean) {
        itemStack.apply {
            this.editMeta {
                it.addEnchant(enchantmentAttr, levelAttr, ignoreRestrictionsAttr)
            }
        }
    }

    @Beta
            /**
             * `itemStack.apply { this.addUnsafeEnchantment(enchantmentAttr(), levelAttr()) }`
             *
             * @param enchantmentAttr () -> Enchantment
             * @param levelAttr () -> Int
             */
    fun unsafeEnchantment(enchantmentAttr:  Enchantment, levelAttr:  Int) {
        itemStack.apply {
            this.addUnsafeEnchantment(enchantmentAttr, levelAttr)
        }
    }

    /**
     * `displayName` takes a lambda that returns a `Component` and applies it to the `ItemStack`'s meta
     *
     * @param nameAttr () -> Component
     */
    fun displayName(nameAttr: Component) {
        itemStack.apply {
            editMeta {
                it.displayName(nameAttr)
            }
        }
    }

    /**
     * `itemStack.apply { editMeta { it.setCustomModelData(idAttr()) } }`
     *
     * @param idAttr () -> Int
     */
    fun customModelData(idAttr: Int) {
        itemStack.apply {
            editMeta {
                it.setCustomModelData(idAttr)
            }
        }
    }

    fun <T, Z : Any> dataContainer(namespacedKey: NamespacedKey, pdt: PersistentDataType<T,Z>, value: Z) {
        itemStack.apply {
            editMeta {
                it.persistentDataContainer.apply {
                    this.set(namespacedKey, pdt, value)
                }
            }
        }
    }

    /**
     * `unbreakable` is a function that takes a lambda as an argument, and the lambda returns an integer
     *
     * @param idAttr () -> Int
     */
    fun unbreakable(idAttr: Int) {
        itemStack.apply {
            editMeta {
                it.setCustomModelData(idAttr)
            }
        }
    }

    /**
     * `itemFlag` is a function that takes a lambda as a parameter, and the lambda returns an array of ItemFlags
     *
     * @param flagAttr () -> Array<ItemFlag>
     */
    fun itemFlag(flagAttr: Array<ItemFlag>) {
        itemStack.apply {
            editMeta {
                addItemFlags(*flagAttr)
            }
        }
    }

    /**
     * `lore` is a function that takes a lambda as an argument, and the lambda returns an array of `Component`s.
     *
     * @param loreAttr () -> Array<Component>
     */
    fun lore(loreAttr: Array<Component>) {
        itemStack.apply {
            editMeta {
                val lore = it.lore() ?: mutableListOf()
                lore.addAll(loreAttr)
                it.lore(lore)
            }
        }
    }

    /**
     * `setLore` takes a lambda that returns an array of `Component`s and sets the item's lore to the result of the lambda
     *
     * @param loreAttr () -> Array<Component>
     */
    fun setLore(loreAttr: Array<Component>) {
        itemStack.apply {
            editMeta {
                lore(loreAttr.toList())
            }
        }
    }

    /**
     * It adds a line of lore to an itemstack
     *
     * @param loreAttr () -> Component
     * @param lineAttr () -> Int
     */
    fun loreLine(loreAttr: Component, lineAttr: Int) {
        itemStack.apply {
            editMeta {
                val lore = it.lore()
                if (lore == null) {
                    lore(mutableListOf(loreAttr))
                } else if (lore.size < lineAttr) {
                    lore.add(loreAttr)
                } else {
                    lore[lineAttr] = loreAttr
                }
                lore(lore)
            }
        }
    }

    /**
     * `repairCosts` takes a function that returns an integer and sets the repair cost of the item to that integer
     *
     * @param repairCosts A lambda that returns the repair cost of the item.
     */
    fun repairCosts(repairCosts: Int) {
        itemStack.apply {
            editMeta {
                if (it is Repairable) {
                    it.repairCost = repairCosts
                }
            }
        }
    }

    /**
     * `This function returns the itemStack variable.`
     *
     * @return The itemStack
     */
    fun playerProfile(profile: PlayerProfile) {
        itemStack.apply {
            editMeta {
                if (it is SkullMeta) {
                    it.playerProfile = profile
                }
            }
        }
    }

    fun skullTexture(texture: String) {
        itemStack.apply {
            editMeta {
                if (it is SkullMeta) {
                    it.playerProfile = Bukkit.createProfile(UUID.randomUUID()).apply {
                        this.setProperty(ProfileProperty("textures", texture))
                        // {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/187baa4767234c01c04b8bbeb518a053dce739f4a04358a424302fb4a0172f8"}}}
                    }
                }
            }
        }
    }

    fun build(): ItemStack {
        return this.itemStack
    }
}

/**
 * `itemBuilder` is a function that takes a lambda as a parameter, and returns an ItemStack
 *
 * @param init This is the function that will be called when the function is called.
 */
fun itemBuilder(init: ItemBuilder.() -> Unit): ItemStack = ItemBuilder(init).build()