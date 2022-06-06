package com.truesightid.data


import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.truesightid.data.source.local.entity.ClaimEntity
import com.truesightid.data.source.local.room.LocalDataSource
import com.truesightid.data.source.remote.ApiResponse
import com.truesightid.data.source.remote.RemoteDataSource
import com.truesightid.data.source.remote.request.ClaimRequest
import com.truesightid.data.source.remote.request.LoginRequest
import com.truesightid.data.source.remote.request.PostClaimRequest
import com.truesightid.data.source.remote.response.ClaimsResponse
import com.truesightid.data.source.remote.response.LoginResponse
import com.truesightid.data.source.remote.response.PostClaimResponse
import com.truesightid.utils.AppExecutors
import com.truesightid.utils.Resource

class TrueSightRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val appExecutor: AppExecutors
) : TrueSightDataSource {
    companion object {
        @Volatile
        private var instance: TrueSightRepository? = null

        fun getInstance(
            remoteData: RemoteDataSource,
            localData: LocalDataSource,
            appExecutors: AppExecutors
        ): TrueSightRepository =
            instance ?: synchronized(this) {
                TrueSightRepository(remoteData, localData, appExecutors).apply {
                    instance = this
                }
            }
    }

    override fun upVoteClaimById(id: Int) =
        remoteDataSource.upVoteRequestById(id)


    override fun downVoteClaimById(id: Int) =
        remoteDataSource.downVoteRequestById(id)


    override fun loginRequest(loginRequest: LoginRequest): LiveData<ApiResponse<LoginResponse>> =
        remoteDataSource.loginRequest(loginRequest)

    override fun postClaim(postClaimRequest: PostClaimRequest): LiveData<ApiResponse<PostClaimResponse>> =
        remoteDataSource.postClaimRequest(postClaimRequest)

    override fun deleteLocalClaims() = localDataSource.deleteLocalClaims()


    override fun getAllClaims(request: ClaimRequest): LiveData<Resource<PagedList<ClaimEntity>>> {
        return object : NetworkBoundResource<PagedList<ClaimEntity>, ClaimsResponse>(appExecutor) {
            override fun loadFromDB(): LiveData<PagedList<ClaimEntity>> {
                val config = PagedList.Config.Builder()
                    .setEnablePlaceholders(false)
                    .setInitialLoadSizeHint(4)
                    .setPageSize(4)
                    .build()
                return LivePagedListBuilder(localDataSource.getAllClaims(), config).build()
            }


            override fun shouldFetch(data: PagedList<ClaimEntity>?): Boolean =
                data == null || data.isEmpty()


            override fun createCall(): LiveData<ApiResponse<ClaimsResponse>> =
                remoteDataSource.getAllClaims(request)

            override fun saveCallResult(data: ClaimsResponse) {
                val body = data.data
                val claimList = ArrayList<ClaimEntity>()
                if (body != null) {
                    for (response in body) {
                        val claim = response?.dateCreated?.let {
                            ClaimEntity(
                                response.id as Int,
                                response.title as String,
                                response.authorUsername as String,
                                response.description as String,
                                response.attachment?.get(0) as String,
                                response.fake as Int,
                                response.upvote as Int,
                                response.downvote as Int,
                                it.toFloat()
                            )
                        }
                        if (claim != null) {
                            claimList.add(claim)
                        }
                    }
                }
                localDataSource.insertClaims(claimList)
            }
        }.asLiveData()
    }
}