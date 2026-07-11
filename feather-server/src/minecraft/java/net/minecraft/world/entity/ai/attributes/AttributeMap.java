package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeMap implements java.util.function.Consumer<AttributeInstance> { // Leaf - optimize attribute
    // Leaf start - optimize attribute
    public final Map<Holder<Attribute>, AttributeInstance> attributes = new net.feathermc.feather.util.map.AttributeInstanceArrayMap();
    private final Set<AttributeInstance> attributesToSync = new net.feathermc.feather.util.map.AttributeInstanceSet((net.feathermc.feather.util.map.AttributeInstanceArrayMap) attributes);
    private final Set<AttributeInstance> attributesToUpdate = new net.feathermc.feather.util.map.AttributeInstanceSet((net.feathermc.feather.util.map.AttributeInstanceArrayMap) attributes);
    // Leaf end - optimize attribute
    private final AttributeSupplier supplier;
    private final net.minecraft.world.entity.LivingEntity entity; // Purpur - Ridables

    public AttributeMap(final AttributeSupplier supplier) {
        // Purpur start - Ridables
        this(supplier, null);
    }
    public AttributeMap(AttributeSupplier defaultAttributes, net.minecraft.world.entity.LivingEntity entity) {
        this.entity = entity instanceof net.minecraft.world.entity.ambient.Bat ? entity : null; // Leaf - optimize attribute - only check bat
        // Purpur end - Ridables
        this.supplier = defaultAttributes;
    }

    private void onAttributeModified(final AttributeInstance attributeInstance) {
        // Leaf start - optimize attribute
        Attribute attribute = attributeInstance.getAttribute().value();
        ((net.feathermc.feather.util.map.AttributeInstanceSet) this.attributesToUpdate).addAttribute(attribute);
        if (attribute.isClientSyncable() && (entity == null || entity.shouldSendAttribute(attribute))) { // Purpur - Ridables
            ((net.feathermc.feather.util.map.AttributeInstanceSet) this.attributesToSync).addAttribute(attribute);
        }
        // Leaf end - optimize attribute
    }

    // Leaf start - optimize attribute
    @Override
    public void accept(AttributeInstance instance) {
        this.onAttributeModified(instance);
    }
    private static final AttributeInstance[] EMPTY_ATTRIBUTE_INSTANCE = new AttributeInstance[0];
    @Deprecated
    public Set<AttributeInstance> getAttributesToSync() {
        if (attributesToSync.isEmpty()) {
            return Set.of();
        }
        it.unimi.dsi.fastutil.objects.ReferenceArraySet<AttributeInstance> clone = it.unimi.dsi.fastutil.objects.ReferenceArraySet.ofUnchecked(attributesToSync.toArray(EMPTY_ATTRIBUTE_INSTANCE));
        this.attributesToSync.clear();
        return clone;
    }

    @Deprecated
    public Set<AttributeInstance> getAttributesToUpdate() {
        if (attributesToUpdate.isEmpty()) {
            return Set.of();
        }
        it.unimi.dsi.fastutil.objects.ReferenceArraySet<AttributeInstance> clone = it.unimi.dsi.fastutil.objects.ReferenceArraySet.ofUnchecked(attributesToUpdate.toArray(EMPTY_ATTRIBUTE_INSTANCE));
        this.attributesToUpdate.clear();
        return clone;
    }

    public boolean attributeDirty() {
        return !attributesToSync.isEmpty();
    }

    public int[] getAttributesToUpdateIds() {
        int[] clone = ((net.feathermc.feather.util.map.AttributeInstanceSet) attributesToUpdate).inner.toIntArray();
        this.attributesToUpdate.clear();
        return clone;
    }

    public int[] getAttributesToSyncIds() {
        int[] clone = ((net.feathermc.feather.util.map.AttributeInstanceSet) attributesToSync).inner.toIntArray();
        this.attributesToSync.clear();
        return clone;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        List<AttributeInstance> list = new ArrayList<>(this.attributes.size());
        for (AttributeInstance instance : this.attributes.values()) {
            if (instance.getAttribute().value().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute().value()))) { // Purpur - Ridables
                list.add(instance);
            }
        }
        return list;
    }

    public List<net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot> getSyncableAttributesPacket() {
        List<net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot> list = new ArrayList<>(attributes.size());
        if (attributes instanceof net.feathermc.feather.util.map.AttributeInstanceArrayMap map) {
            for (AttributeInstance instance : map.elements()) {
                if (instance != null && instance.getAttribute().value().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute().value()))) { // Purpur - Ridables
                    list.add(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot(
                        instance.getAttribute(), instance.getBaseValue(), instance.getModifiers()
                    ));
                }
            }
        } else {
            for (AttributeInstance instance : attributes.values()) {
                if (instance != null && instance.getAttribute().value().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute().value()))) { // Purpur - Ridables
                    list.add(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot(
                        instance.getAttribute(), instance.getBaseValue(), instance.getModifiers()
                    ));
                }
            }
        }
        return list;
    }

    public @Nullable AttributeInstance getInstance(final Holder<Attribute> attribute) {
        AttributeInstance v;
        if ((v = this.attributes.get(attribute)) == null) {
            AttributeInstance newValue;
            if ((newValue = this.supplier.createInstance(this, attribute)) != null) {
                attributes.put(attribute, newValue);
                return newValue;
            }
        }
        return v;
    }
    // Leaf end - optimize attribute

    public boolean hasAttribute(final Holder<Attribute> attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasModifier(final Holder<Attribute> attribute, final Identifier id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id) != null : this.supplier.hasModifier(attribute, id);
    }

    public double getValue(final Holder<Attribute> attribute) {
        AttributeInstance ownAttribute = this.attributes.get(attribute);
        return ownAttribute != null ? ownAttribute.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(final Holder<Attribute> attribute) {
        AttributeInstance ownAttribute = this.attributes.get(attribute);
        return ownAttribute != null ? ownAttribute.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(final Holder<Attribute> attribute, final Identifier id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id).amount() : this.supplier.getModifierValue(attribute, id);
    }

    public void addTransientAttributeModifiers(final Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.forEach((attribute, attributeModifier) -> {
            AttributeInstance instance = this.getInstance((Holder<Attribute>)attribute);
            if (instance != null) {
                instance.removeModifier(attributeModifier.id());
                instance.addTransientModifier(attributeModifier);
            }
        });
    }

    public void removeAttributeModifiers(final Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.asMap().forEach((attribute, attributeModifiers) -> {
            AttributeInstance instance = this.attributes.get(attribute);
            if (instance != null) {
                attributeModifiers.forEach(attributeModifier -> instance.removeModifier(attributeModifier.id()));
            }
        });
    }

    public void assignAllValues(final AttributeMap other) {
        other.attributes.values().forEach(otherInstance -> {
            AttributeInstance selfInstance = this.getInstance(otherInstance.getAttribute());
            if (selfInstance != null) {
                selfInstance.replaceFrom(otherInstance);
            }
        });
    }

    public void assignBaseValues(final AttributeMap other) {
        other.attributes.values().forEach(otherInstance -> {
            AttributeInstance selfInstance = this.getInstance(otherInstance.getAttribute());
            if (selfInstance != null) {
                selfInstance.setBaseValue(otherInstance.getBaseValue());
            }
        });
    }

    public void assignPermanentModifiers(final AttributeMap other) {
        other.attributes.values().forEach(otherInstance -> {
            AttributeInstance selfInstance = this.getInstance(otherInstance.getAttribute());
            if (selfInstance != null) {
                selfInstance.addPermanentModifiers(otherInstance.getPermanentModifiers());
            }
        });
    }

    public boolean resetBaseValue(final Holder<Attribute> attribute) {
        if (!this.supplier.hasAttribute(attribute)) {
            return false;
        }

        AttributeInstance instance = this.attributes.get(attribute);
        if (instance != null) {
            instance.setBaseValue(this.supplier.getBaseValue(attribute));
        }

        return true;
    }

    public List<AttributeInstance.Packed> pack() {
        List<AttributeInstance.Packed> result = new ArrayList<>(this.attributes.values().size());

        for (AttributeInstance attribute : this.attributes.values()) {
            result.add(attribute.pack());
        }

        return result;
    }

    public void apply(final List<AttributeInstance.Packed> packedAttributes) {
        for (AttributeInstance.Packed packedAttribute : packedAttributes) {
            AttributeInstance instance = this.getInstance(packedAttribute.attribute());
            if (instance != null) {
                instance.apply(packedAttribute);
            }
        }
    }

    // Paper - start - living entity allow attribute registration
    public void registerAttribute(Holder<Attribute> attributeBase) {
        AttributeInstance attributeModifiable = new AttributeInstance(attributeBase, AttributeInstance::getAttribute);
        attributes.put(attributeBase, attributeModifiable);
    }
    // Paper - end - living entity allow attribute registration

}
