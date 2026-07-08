package profile

import org.koin.dsl.module
import profile.api.grpc.GrpcPrincipalResolver
import profile.api.grpc.SocialGrpcService
import profile.infrastructure.*
import profile.usecases.*
import javax.sql.DataSource

val socialModule = module {
    single { SocialRepo(get<DataSource>()) }
    single { BlockRepo(get<DataSource>()) }
    single { PrivacyRepo(get<DataSource>()) }
    single { NotificationRepo(get<DataSource>()) }
    single { NotificationOutboxRepo(get<DataSource>()) }

    single<SocialRepository> { get<SocialRepo>() }
    single<BlockRepository> { get<BlockRepo>() }
    single<PrivacyRepository> { get<PrivacyRepo>() }
    single<NotificationRepository> { get<NotificationRepo>() }
    single<NotificationOutboxRepository> { get<NotificationOutboxRepo>() }
    single<NotificationOutboxProcessor> { get<NotificationOutboxRepo>() }

    single { SocialUseCases(get(), get(), get()) }
    single { NotificationUseCases(get(), get()) }
    single { BirthdayNotificationService(get(), get(), get<PrivacyRepository>(), get()) }

    single { SseManager() }

    single { EventBus(get(), get()) }
    single { NotificationOutboxWorker(get(), get(), get(), get(), get()) }

    single { GrpcPrincipalResolver(get(), get()) }
    single { SocialGrpcService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
