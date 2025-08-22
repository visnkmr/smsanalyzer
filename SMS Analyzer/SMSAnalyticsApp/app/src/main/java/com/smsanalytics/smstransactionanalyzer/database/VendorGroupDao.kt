package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.VendorGroup
import com.smsanalytics.smstransactionanalyzer.model.VendorGroupMember
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorGroupDao {

    @Query("SELECT * FROM vendor_groups ORDER BY name ASC")
    fun getAllVendorGroups(): Flow<List<VendorGroup>>

    @Query("SELECT * FROM vendor_groups WHERE id = :id")
    suspend fun getVendorGroupById(id: Long): VendorGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorGroup(vendorGroup: VendorGroup): Long

    @Update
    suspend fun updateVendorGroup(vendorGroup: VendorGroup)

    @Delete
    suspend fun deleteVendorGroup(vendorGroup: VendorGroup)

    // Vendor Group Member operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorGroupMember(member: VendorGroupMember): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorGroupMembers(members: List<VendorGroupMember>)

    @Query("DELETE FROM vendor_group_members WHERE groupId = :groupId")
    suspend fun deleteMembersByGroupId(groupId: Long)

    @Query("DELETE FROM vendor_group_members WHERE vendorId = :vendorId")
    suspend fun deleteMembersByVendorId(vendorId: Long)

    @Query("SELECT * FROM vendor_group_members WHERE groupId = :groupId")
    fun getMembersByGroupId(groupId: Long): Flow<List<VendorGroupMember>>

    @Query("SELECT * FROM vendor_group_members")
    fun getAllGroupMembers(): Flow<List<VendorGroupMember>>

    @Query("SELECT * FROM vendors WHERE id IN (SELECT vendorId FROM vendor_group_members WHERE groupId = :groupId) AND isExcluded = 0")
    fun getVendorsByGroupId(groupId: Long): Flow<List<Vendor>>
}