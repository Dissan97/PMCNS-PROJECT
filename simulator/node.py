from simulator.event import Event
from simulator.job import Job
from simulator.scheduler import NextEventScheduler


class Node:
    def __init__(self, name, service_rates):

        """
            The class node which represent a Server
        """

        """
            The node name: Example A - B - P
        """
        self.name = name
        """
            Service rates for a job
        """
        self.service_rates = service_rates
        """
            List of job that can be in queue or in service
        """
        self.jobs: list[Job] = []
        """
            Last update for processor sharing
        """
        self.last_update = 0

    def _update_remaining(self, now):
        """
            Update remaining time for a job
        """
        if not self.jobs:
            self.last_update = now
            return
        elapsed = now - self.last_update
        share = elapsed / len(self.jobs)
        for job in self.jobs:
            mu = self.service_rates[job.job_class]
            job.remaining_service = max(0.0,
                job.remaining_service - share * mu)
        self.last_update = now

    def arrival(self, job: Job, scheduler: NextEventScheduler):
        """
        Job Arrival
        :param job: the job that must be scheduled
        :param scheduler: the scheduler to be used
        """
        self._update_remaining(scheduler.current_time)
        self.jobs.append(job)
        self._schedule_departure(scheduler)

    def departure(self, job: Job, scheduler: NextEventScheduler):
        """
        Job departure
        :param job: the job that must be scheduled
        :param scheduler: the scheduler to be used
        """
        self._update_remaining(scheduler.current_time)
        self.jobs = [j for j in self.jobs if j.id != job.id]
        self._schedule_departure(scheduler)

    def _schedule_departure(self, scheduler: NextEventScheduler):
        """
        Schedule departure the job with the shortest remaining time is chosen
        """
        if not self.jobs: return

        min_job = min(self.jobs,
                      key=lambda j: j.remaining_service / self.service_rates[j.job_class])
        ttf = (min_job.remaining_service * len(self.jobs)
               / self.service_rates[min_job.job_class])

        e = Event(time=scheduler.current_time + ttf,
                  event_type="DEPARTURE",
                  server=self.name,
                  job_id=min_job.id,
                  job_class=min_job.job_class)
        scheduler.schedule(e)


    def __repr__(self):
        return f"Node(name={self.name}, service_rates={self.service_rates}, last_update={self.last_update})"

    def __str__(self):
        return self.__repr__()