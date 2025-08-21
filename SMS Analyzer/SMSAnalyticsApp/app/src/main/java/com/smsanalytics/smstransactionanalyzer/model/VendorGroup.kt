package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vendor_groups")
data class VendorGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String = "#FF5722" // Default color
)

@Entity(tableName = "vendor_group_members")
data class VendorGroupMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val vendorId: Long
)

data class VendorGroupWithVendors(
    val group: VendorGroup,
    val vendors: List<Vendor>,
    val totalSpent: Double
)