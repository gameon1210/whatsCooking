package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveMembers(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveMembers(): List<Member>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: Long): Member?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: Member): Long

    @Update
    suspend fun updateMember(member: Member)

    // Deactivate — never hard delete
    @Query("UPDATE members SET isActive = 0 WHERE id = :id")
    suspend fun deactivateMember(id: Long)
}
